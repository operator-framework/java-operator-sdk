package io.javaoperatorsdk.operator.junit;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.fabric8.kubernetes.client.utils.Utils;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider.override;

public class OperatorExtension
    implements HasKubernetesClient,
    BeforeAllCallback,
    BeforeEachCallback,
    AfterAllCallback,
    AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperatorExtension.class);

  private final KubernetesClient kubernetesClient;
  private final ConfigurationService configurationService;
  private final Operator operator;
  private final List<ControllerSpec> controllers;
  private final boolean preserveNamespaceOnError;
  private final boolean waitForNamespaceDeletion;

  private String namespace;

  private OperatorExtension(
      ConfigurationService configurationService,
      List<ControllerSpec> controllers,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion) {

    this.kubernetesClient = new DefaultKubernetesClient();
    this.configurationService = configurationService;
    this.controllers = controllers;
    this.operator = new Operator(this.kubernetesClient, this.configurationService);
    this.preserveNamespaceOnError = preserveNamespaceOnError;
    this.waitForNamespaceDeletion = waitForNamespaceDeletion;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    before(context);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    before(context);
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    after(context);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    after(context);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  public String getNamespace() {
    return namespace;
  }

  @SuppressWarnings({"rawtypes"})
  public List<ResourceController> getControllers() {
    return operator.getControllers().stream()
        .map(ConfiguredController::getController)
        .collect(Collectors.toUnmodifiableList());
  }

  @SuppressWarnings({"rawtypes"})
  public <T extends ResourceController> T getControllerOfType(Class<T> type) {
    return operator.getControllers().stream()
        .map(ConfiguredController::getController)
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unable to find a controller of type: " + type));
  }

  public <T extends HasMetadata> NonNamespaceOperation<T, KubernetesResourceList<T>, Resource<T>> resources(
      Class<T> type) {
    return kubernetesClient.resources(type).inNamespace(namespace);
  }

  public <T extends HasMetadata> T getNamedResource(Class<T> type, String name) {
    return kubernetesClient.resources(type).inNamespace(namespace).withName(name).get();
  }

  public <T extends HasMetadata> T create(Class<T> type, T resource) {
    return kubernetesClient.resources(type).inNamespace(namespace).create(resource);
  }

  @SuppressWarnings("unchecked")
  protected void before(ExtensionContext context) {
    namespace = context.getRequiredTestClass().getSimpleName();
    namespace += "-";
    namespace += context.getRequiredTestMethod().getName();
    namespace = KubernetesResourceUtil.sanitizeName(namespace).toLowerCase(Locale.US);
    namespace = namespace.substring(0, Math.min(namespace.length(), 63));

    LOGGER.info("Initializing integration test in namespace {}", namespace);

    kubernetesClient
        .namespaces()
        .create(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());


    for (var ref : controllers) {
      final var config = configurationService.getConfigurationFor(ref.controller);
      final var oconfig = override(config).settingNamespace(namespace);
      final var path = "/META-INF/fabric8/" + config.getCRDName() + "-v1.yml";

      if (ref.retry != null) {
        oconfig.withRetry(ref.retry);
      }

      try (InputStream is = getClass().getResourceAsStream(path)) {
        kubernetesClient.load(is).createOrReplace();
      } catch (Exception ex) {
        throw new IllegalStateException("Cannot apply CRD yaml: " + path, ex);
      }

      if (ref.controller instanceof KubernetesClientAware) {
        ((KubernetesClientAware) ref.controller).setKubernetesClient(kubernetesClient);
      }


      this.operator.register(ref.controller, oconfig.build());
    }

    this.operator.start();
  }

  protected void after(ExtensionContext context) {
    if (namespace != null) {
      if (preserveNamespaceOnError && context.getExecutionException().isPresent()) {
        LOGGER.info("Preserving namespace {}", namespace);
      } else {
        LOGGER.info("Deleting namespace {} and stopping operator", namespace);
        kubernetesClient.namespaces().withName(namespace).delete();
        if (waitForNamespaceDeletion) {
          LOGGER.info("Waiting for namespace {} to be deleted", namespace);
          Awaitility.await("namespace deleted")
              .pollInterval(50, TimeUnit.MILLISECONDS)
              .atMost(60, TimeUnit.SECONDS)
              .until(() -> kubernetesClient.namespaces().withName(namespace).get() == null);
        }
      }
    }

    try {
      this.operator.close();
    } catch (Exception e) {
      // ignored
    }
  }

  @SuppressWarnings("rawtypes")
  public static class Builder {
    private final List<ControllerSpec> controllers;
    private ConfigurationService configurationService;
    private boolean preserveNamespaceOnError;
    private boolean waitForNamespaceDeletion;

    protected Builder() {
      this.configurationService = new BaseConfigurationService(Version.UNKNOWN);
      this.controllers = new ArrayList<>();

      this.preserveNamespaceOnError = Utils.getSystemPropertyOrEnvVar(
          "josdk.it.preserveNamespaceOnError",
          false);

      this.waitForNamespaceDeletion = Utils.getSystemPropertyOrEnvVar(
          "josdk.it.waitForNamespaceDeletion",
          true);
    }

    public Builder preserveNamespaceOnError(boolean value) {
      this.preserveNamespaceOnError = value;
      return this;
    }

    public Builder waitForNamespaceDeletion(boolean value) {
      this.waitForNamespaceDeletion = value;
      return this;
    }

    public Builder withConfigurationService(ConfigurationService value) {
      configurationService = value;
      return this;
    }

    @SuppressWarnings("rawtypes")
    public Builder withController(ResourceController value) {
      controllers.add(new ControllerSpec(value, null));
      return this;
    }

    @SuppressWarnings("rawtypes")
    public Builder withController(ResourceController value, Retry retry) {
      controllers.add(new ControllerSpec(value, retry));
      return this;
    }

    @SuppressWarnings("rawtypes")
    public Builder withController(Class<? extends ResourceController> value) {
      try {
        controllers.add(new ControllerSpec(value.getConstructor().newInstance(), null));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public OperatorExtension build() {
      return new OperatorExtension(
          configurationService,
          controllers,
          preserveNamespaceOnError,
          waitForNamespaceDeletion);
    }
  }

  @SuppressWarnings("rawtypes")
  private static class ControllerSpec {
    final ResourceController controller;
    final Retry retry;

    public ControllerSpec(
        ResourceController controller,
        Retry retry) {
      this.controller = controller;
      this.retry = retry;
    }
  }
}
