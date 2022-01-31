package io.javaoperatorsdk.operator.junit;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider.override;

public class OperatorExtension extends AbstractOperatorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperatorExtension.class);

  private final Operator operator;
  private final List<ReconcilerSpec> reconcilers;

  private OperatorExtension(
      ConfigurationService configurationService,
      List<ReconcilerSpec> reconcilers,
      List<HasMetadata> infrastructure,
      Duration infrastructureTimeout,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion,
      boolean oneNamespacePerClass) {
    super(configurationService, infrastructure, infrastructureTimeout, oneNamespacePerClass,
        preserveNamespaceOnError,
        waitForNamespaceDeletion);
    this.reconcilers = reconcilers;
    this.operator = new Operator(this.kubernetesClient, this.configurationService);
  }

  /**
   * Creates a {@link Builder} to set up an {@link OperatorExtension} instance.
   *
   * @return the builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  @SuppressWarnings({"rawtypes"})
  public List<Reconciler> getReconcilers() {
    return operator.getControllers().stream()
        .map(Controller::getReconciler)
        .collect(Collectors.toUnmodifiableList());
  }

  public Reconciler getFirstReconciler() {
    return operator.getControllers().stream()
        .map(Controller::getReconciler)
        .findFirst().orElseThrow();
  }

  @SuppressWarnings({"rawtypes"})
  public <T extends Reconciler> T getControllerOfType(Class<T> type) {
    return operator.getControllers().stream()
        .map(Controller::getReconciler)
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unable to find a reconciler of type: " + type));
  }

  @SuppressWarnings("unchecked")
  protected void before(ExtensionContext context) {
    LOGGER.info("Initializing integration test in namespace {}", namespace);

    kubernetesClient
        .namespaces()
        .create(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());

    kubernetesClient
        .resourceList(infrastructure)
        .createOrReplace();
    kubernetesClient
        .resourceList(infrastructure)
        .waitUntilReady(infrastructureTimeout.toMillis(), TimeUnit.MILLISECONDS);

    for (var ref : reconcilers) {
      final var config = configurationService.getConfigurationFor(ref.reconciler);
      final var oconfig = override(config).settingNamespace(namespace);
      final var path = "/META-INF/fabric8/" + config.getResourceTypeName() + "-v1.yml";

      if (ref.retry != null) {
        oconfig.withRetry(ref.retry);
      }

      try (InputStream is = getClass().getResourceAsStream(path)) {
        final var crd = kubernetesClient.load(is);
        crd.createOrReplace();
        crd.waitUntilReady(2, TimeUnit.SECONDS);
        LOGGER.debug("Applied CRD with name: {}", config.getResourceTypeName());
      } catch (Exception ex) {
        throw new IllegalStateException("Cannot apply CRD yaml: " + path, ex);
      }

      if (ref.reconciler instanceof KubernetesClientAware) {
        ((KubernetesClientAware) ref.reconciler).setKubernetesClient(kubernetesClient);
      }


      this.operator.register(ref.reconciler, oconfig.build());
    }

    LOGGER.debug("Starting the operator locally");
    this.operator.start();
  }

  protected void after(ExtensionContext context) {
    if (namespace != null) {
      if (preserveNamespaceOnError && context.getExecutionException().isPresent()) {
        LOGGER.info("Preserving namespace {}", namespace);
      } else {
        kubernetesClient.resourceList(infrastructure).delete();
        LOGGER.info("Deleting namespace {} and stopping operator", namespace);
        kubernetesClient.namespaces().withName(namespace).delete();
        if (waitForNamespaceDeletion) {
          LOGGER.info("Waiting for namespace {} to be deleted", namespace);
          Awaitility.await("namespace deleted")
              .pollInterval(50, TimeUnit.MILLISECONDS)
              .atMost(90, TimeUnit.SECONDS)
              .until(() -> kubernetesClient.namespaces().withName(namespace).get() == null);
        }
      }
    }

    try {
      this.operator.stop();
    } catch (Exception e) {
      // ignored
    }
  }

  @SuppressWarnings("rawtypes")
  public static class Builder extends AbstractBuilder {
    private final List<ReconcilerSpec> reconcilers;
    private ConfigurationService configurationService;

    protected Builder() {
      super();
      this.configurationService = new BaseConfigurationService(Version.UNKNOWN);
      this.reconcilers = new ArrayList<>();
    }

    public Builder preserveNamespaceOnError(boolean value) {
      this.preserveNamespaceOnError = value;
      return this;
    }

    public Builder waitForNamespaceDeletion(boolean value) {
      this.waitForNamespaceDeletion = value;
      return this;
    }

    public Builder oneNamespacePerClass(boolean value) {
      this.oneNamespacePerClass = value;
      return this;
    }

    public Builder withConfigurationService(ConfigurationService value) {
      configurationService = value;
      return this;
    }

    public Builder withInfrastructureTimeout(Duration value) {
      infrastructureTimeout = value;
      return this;
    }

    public Builder withInfrastructure(List<HasMetadata> hm) {
      infrastructure.addAll(hm);
      return this;
    }

    public Builder withInfrastructure(HasMetadata... hms) {
      for (HasMetadata hm : hms) {
        infrastructure.add(hm);
      }
      return this;
    }

    @SuppressWarnings("rawtypes")
    public Builder withReconciler(Reconciler value) {
      reconcilers.add(new ReconcilerSpec(value, null));
      return this;
    }

    @SuppressWarnings("rawtypes")
    public Builder withReconciler(Reconciler value, Retry retry) {
      reconcilers.add(new ReconcilerSpec(value, retry));
      return this;
    }

    @SuppressWarnings("rawtypes")
    public Builder withReconciler(Class<? extends Reconciler> value) {
      try {
        reconcilers.add(new ReconcilerSpec(value.getConstructor().newInstance(), null));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public OperatorExtension build() {
      return new OperatorExtension(
          configurationService,
          reconcilers,
          infrastructure,
          infrastructureTimeout,
          preserveNamespaceOnError,
          waitForNamespaceDeletion,
          oneNamespacePerClass);
    }
  }

  @SuppressWarnings("rawtypes")
  private static class ReconcilerSpec {
    final Reconciler reconciler;
    final Retry retry;

    public ReconcilerSpec(Reconciler reconciler, Retry retry) {
      this.reconciler = reconciler;
      this.retry = retry;
    }
  }
}
