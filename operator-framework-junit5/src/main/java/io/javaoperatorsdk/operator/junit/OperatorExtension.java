package io.javaoperatorsdk.operator.junit;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider.override;

@SuppressWarnings("rawtypes")
public class OperatorExtension extends AbstractOperatorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperatorExtension.class);

  private final Operator operator;
  private final List<ReconcilerSpec> reconcilers;

  private OperatorExtension(
      ConfigurationService configurationService,
      List<ReconcilerSpec> reconcilers,
      List<HasMetadata> infrastructure,
      List<PortFowardSpec> portForwards,
      Duration infrastructureTimeout,
      Consumer<ConditionFactory> infrastructureAwaiter, boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion,
      boolean oneNamespacePerClass) {
    super(
        configurationService,
        infrastructure,
        infrastructureTimeout,
        infrastructureAwaiter,
        portForwards,
        oneNamespacePerClass,
        preserveNamespaceOnError,
        waitForNamespaceDeletion);
    this.reconcilers = reconcilers;
    this.operator = new Operator(getKubernetesClient(), this.configurationService);
  }

  /**
   * Creates a {@link Builder} to set up an {@link OperatorExtension} instance.
   *
   * @return the builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  private Stream<Reconciler> reconcilers() {
    return operator.getControllers().stream().map(Controller::getReconciler);
  }

  public List<Reconciler> getReconcilers() {
    return reconcilers().collect(Collectors.toUnmodifiableList());
  }

  public Reconciler getFirstReconciler() {
    return reconcilers().findFirst().orElseThrow();
  }

  public <T extends Reconciler> T getControllerOfType(Class<T> type) {
    return reconcilers()
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unable to find a reconciler of type: " + type));
  }

  @SuppressWarnings("unchecked")
  protected void before(ExtensionContext context) {
    super.before(context);

    final var kubernetesClient = getKubernetesClient();

    for (var ref : reconcilers) {
      final var config = configurationService.getConfigurationFor(ref.reconciler);
      final var oconfig = override(config).settingNamespace(namespace);
      final var path = "/META-INF/fabric8/" + config.getResourceTypeName() + "-v1.yml";

      if (ref.retry != null) {
        oconfig.withRetry(ref.retry);
      }
      if (ref.controllerConfigurationOverrider != null) {
        ref.controllerConfigurationOverrider.accept(oconfig);
      }

      try (InputStream is = getClass().getResourceAsStream(path)) {
        final var crd = kubernetesClient.load(is);
        crd.createOrReplace();
        Thread.sleep(CRD_READY_WAIT); // readiness is not applicable for CRD, just wait a little
        LOGGER.debug("Applied CRD with name: {}", config.getResourceTypeName());
      } catch (InterruptedException ex) {
        LOGGER.error("Interrupted.", ex);
        Thread.currentThread().interrupt();
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

  @Override
  protected void deleteOperator() {
    try {
      this.operator.stop();
    } catch (Exception e) {
      // ignored
    }
  }

  @SuppressWarnings("rawtypes")
  public static class Builder extends AbstractBuilder<Builder> {
    private final List<ReconcilerSpec> reconcilers;

    protected Builder() {
      super();
      this.reconcilers = new ArrayList<>();
    }

    public Builder withReconciler(
        Reconciler value, Consumer<ControllerConfigurationOverrider> configurationOverrider) {
      return withReconciler(value, null, configurationOverrider);
    }

    public Builder withReconciler(
        Reconciler value,
        Retry retry,
        Consumer<ControllerConfigurationOverrider> configurationOverrider) {
      reconcilers.add(new ReconcilerSpec(value, retry, configurationOverrider));
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
          portForwards,
          infrastructureTimeout,
          infrastructureAwaiter,
          preserveNamespaceOnError,
          waitForNamespaceDeletion,
          oneNamespacePerClass);
    }
  }

  @SuppressWarnings("rawtypes")
  private static class ReconcilerSpec {
    final Reconciler reconciler;
    final Retry retry;
    final Consumer<ControllerConfigurationOverrider> controllerConfigurationOverrider;

    public ReconcilerSpec(Reconciler reconciler, Retry retry) {
      this(reconciler, retry, null);
    }

    public ReconcilerSpec(
        Reconciler reconciler,
        Retry retry,
        Consumer<ControllerConfigurationOverrider> controllerConfigurationOverrider) {
      this.reconciler = reconciler;
      this.retry = retry;
      this.controllerConfigurationOverrider = controllerConfigurationOverrider;
    }
  }
}
