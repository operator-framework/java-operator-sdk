package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@Disabled("issue with fabric8 v6")
@EnableKubernetesMockClient(crud = true, https = false)
class CustomResourceSelectorTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomResourceSelectorTest.class);
  public static final String NAMESPACE = "test";

  KubernetesMockServer server;
  KubernetesClient client;
  ConfigurationService configurationService;

  @BeforeEach
  void setUpResources() {
    // need to reset the provider if we plan on changing the configuration service since it likely
    // has already been set in previous tests
    ConfigurationServiceProvider.reset();

    configurationService = spy(ConfigurationService.class);
    when(configurationService.checkCRDAndValidateLocalModel()).thenReturn(false);
    when(configurationService.getVersion()).thenReturn(new Version("1", "1", new Date()));
    // make sure not the same config instance is used for the controller, so rate limiter is not
    // shared
    when(configurationService.getConfigurationFor(any(MyController.class)))
        .thenReturn(new MyConfiguration())
        .thenReturn(new MyConfiguration());
  }

  @Test
  void resourceWatchedByLabel() {
    assertThat(server).isNotNull();
    assertThat(client).isNotNull();

    Operator o1 = new Operator(client, configurationService);
    Operator o2 = new Operator(client, configurationService);
    try {
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
          (overrider) -> overrider.settingNamespace(NAMESPACE).withLabelSelector("app=foo"));
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
          (overrider) -> overrider.settingNamespace(NAMESPACE).withLabelSelector("app=bar"));
      o2.start();

      client.resources(TestCustomResource.class).inNamespace(NAMESPACE).create(newMyResource("foo",
          NAMESPACE));
      client.resources(TestCustomResource.class).inNamespace(NAMESPACE).create(newMyResource("bar",
          NAMESPACE));

      await()
          .atMost(5, TimeUnit.SECONDS)
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
    } finally {
      o1.stop();
      o2.stop();
    }

  }

  public TestCustomResource newMyResource(String app, String namespace) {
    TestCustomResource resource = new TestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(app).addToLabels("app", app).build());
    resource.getMetadata().setNamespace(namespace);
    return resource;
  }

  public static class MyConfiguration extends DefaultControllerConfiguration<TestCustomResource> {

    public MyConfiguration() {
      super(MyController.class.getCanonicalName(), "mycontroller", null, Constants.NO_VALUE_SET,
          false, null,
          null, null, null, TestCustomResource.class, null, null, null, null,
          null, null);
    }
  }

  @ControllerConfiguration(namespaces = NAMESPACE)
  public static class MyController implements Reconciler<TestCustomResource> {

    private final Consumer<TestCustomResource> consumer;

    public MyController(Consumer<TestCustomResource> consumer) {
      this.consumer = consumer;
    }

    @Override
    public UpdateControl<TestCustomResource> reconcile(
        TestCustomResource resource, Context<TestCustomResource> context) {

      LOGGER.info("Received event on: {}", resource);

      consumer.accept(resource);
      // patch status now increases generation, this seems to be an issue with the mock server
      return UpdateControl.updateStatus(resource);
    }
  }
}
