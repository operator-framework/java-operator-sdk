package io.javaoperatorsdk.operator.processing.event.internal;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@EnableKubernetesMockClient(crud = true, https = false)
public class CustomResourceSelectorTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomResourceSelectorTest.class);
  public static final String NAMESPACE = "test";

  KubernetesMockServer server;
  KubernetesClient client;
  ConfigurationService configurationService;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUpResources() throws ParseException {
    String buildDate =
        DateTimeFormatter.ofPattern(VersionInfo.VersionKeys.BUILD_DATE_FORMAT)
            .format(LocalDateTime.now());

    server
        .expect()
        .get()
        .withPath("/version")
        .andReturn(
            200,
            new VersionInfo.Builder()
                .withBuildDate(buildDate)
                .withMajor("1")
                .withMinor("21")
                .build())
        .always();

    configurationService = spy(ConfigurationService.class);
    when(configurationService.checkCRDAndValidateLocalModel()).thenReturn(false);
    when(configurationService.getVersion()).thenReturn(new Version("1", "1", new Date()));
    when(configurationService.getConfigurationFor(any(MyController.class))).thenReturn(
        new MyConfiguration(configurationService, null));
  }

  @Test
  void resourceWatchedByLabel() {
    assertThat(server).isNotNull();
    assertThat(client).isNotNull();

    try (Operator o1 = new Operator(client, configurationService);
        Operator o2 = new Operator(client, configurationService)) {

      AtomicInteger c1 = new AtomicInteger();
      AtomicInteger c1err = new AtomicInteger();
      AtomicInteger c2 = new AtomicInteger();
      AtomicInteger c2err = new AtomicInteger();

      o1.register(
          new MyController(
              resource -> {
                if ("foo".equals(resource.getMetadata().getName())) {
                  c1.incrementAndGet();
                }
                if ("bar".equals(resource.getMetadata().getName())) {
                  c1err.incrementAndGet();
                }
              }),
          new MyConfiguration(configurationService, "app=foo"));
      o1.start();
      o2.register(
          new MyController(
              resource -> {
                if ("bar".equals(resource.getMetadata().getName())) {
                  c2.incrementAndGet();
                }
                if ("foo".equals(resource.getMetadata().getName())) {
                  c2err.incrementAndGet();
                }
              }),
          new MyConfiguration(configurationService, "app=bar"));
      o2.start();

      client.resources(TestCustomResource.class).inNamespace(NAMESPACE).create(newMyResource("foo",
          NAMESPACE));
      client.resources(TestCustomResource.class).inNamespace(NAMESPACE).create(newMyResource("bar",
          NAMESPACE));

      await()
          .atMost(325, TimeUnit.SECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .until(() -> c1.get() == 1 && c1err.get() == 0);
      await()
          .atMost(5, TimeUnit.SECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .until(() -> c2.get() == 1 && c2err.get() == 0);

      assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(2, TimeUnit.SECONDS).untilAtomic(c1err, is(greaterThan(0))));
      assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(2, TimeUnit.SECONDS).untilAtomic(c2err, is(greaterThan(0))));
    }
  }

  public TestCustomResource newMyResource(String app, String namespace) {
    TestCustomResource resource = new TestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(app).addToLabels("app", app).build());
    resource.getMetadata().setNamespace(namespace);
    return resource;
  }

  public static class MyConfiguration implements ControllerConfiguration<TestCustomResource> {

    private final String labelSelector;
    private final ConfigurationService service;

    public MyConfiguration(ConfigurationService configurationService, String labelSelector) {
      this.labelSelector = labelSelector;
      this.service = configurationService;
    }

    @Override
    public String getLabelSelector() {
      return labelSelector;
    }

    @Override
    public String getAssociatedControllerClassName() {
      return MyController.class.getCanonicalName();
    }

    @Override
    public Set<String> getNamespaces() {
      return Sets.newSet(NAMESPACE);
    }

    @Override
    public ConfigurationService getConfigurationService() {
      return service;
    }
  }

  @Controller(namespaces = NAMESPACE)
  public static class MyController implements ResourceController<TestCustomResource> {

    private final Consumer<TestCustomResource> consumer;

    public MyController(Consumer<TestCustomResource> consumer) {
      this.consumer = consumer;
    }

    @Override
    public UpdateControl<TestCustomResource> createOrUpdateResource(
        TestCustomResource resource, Context context) {

      LOGGER.info("Received event on: {}", resource);

      consumer.accept(resource);

      return UpdateControl.updateStatusSubResource(resource);
    }
  }
}
