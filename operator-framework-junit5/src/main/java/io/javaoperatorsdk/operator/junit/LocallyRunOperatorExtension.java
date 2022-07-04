package io.javaoperatorsdk.operator.junit;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.RegisteredController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider.override;

@SuppressWarnings("rawtypes")
public class LocallyRunOperatorExtension extends AbstractOperatorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocallyRunOperatorExtension.class);

  private final Operator operator;
  private final List<ReconcilerSpec> reconcilers;
  private final List<PortForwardSpec> portForwards;
  private final List<LocalPortForward> localPortForwards;
  private final List<Class<? extends CustomResource>> additionalCustomResourceDefinitions;
  private final Map<Reconciler, RegisteredController> registeredControllers;

  private LocallyRunOperatorExtension(
      ConfigurationService configurationService,
      List<ReconcilerSpec> reconcilers,
      List<HasMetadata> infrastructure,
      List<PortForwardSpec> portForwards,
      List<Class<? extends CustomResource>> additionalCustomResourceDefinitions,
      Duration infrastructureTimeout,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion,
      boolean oneNamespacePerClass) {
    super(
        configurationService,
        infrastructure,
        infrastructureTimeout,
        oneNamespacePerClass,
        preserveNamespaceOnError,
        waitForNamespaceDeletion);
    this.reconcilers = reconcilers;
    this.portForwards = portForwards;
    this.localPortForwards = new ArrayList<>(portForwards.size());
    this.additionalCustomResourceDefinitions = additionalCustomResourceDefinitions;
    this.operator = new Operator(getKubernetesClient(), this.configurationService);
    this.registeredControllers = new HashMap<>();
  }

  /**
   * Creates a {@link Builder} to set up an {@link LocallyRunOperatorExtension} instance.
   *
   * @return the builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  private Stream<Reconciler> reconcilers() {
    return reconcilers.stream().map(reconcilerSpec -> reconcilerSpec.reconciler);
  }

  public List<Reconciler> getReconcilers() {
    return reconcilers().collect(Collectors.toUnmodifiableList());
  }

  public Reconciler getFirstReconciler() {
    return reconcilers().findFirst().orElseThrow();
  }

  public <T extends Reconciler> T getReconcilerOfType(Class<T> type) {
    return reconcilers()
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unable to find a reconciler of type: " + type));
  }

  public RegisteredController getRegisteredControllerForReconcile(
      Class<? extends Reconciler> type) {
    return registeredControllers.get(getReconcilerOfType(type));
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void before(ExtensionContext context) {
    super.before(context);

    final var kubernetesClient = getKubernetesClient();

    for (var ref : portForwards) {
      String podName = kubernetesClient.pods()
          .inNamespace(ref.getNamespace())
          .withLabel(ref.getLabelKey(), ref.getLabelValue())
          .list()
          .getItems()
          .get(0)
          .getMetadata()
          .getName();

      localPortForwards.add(kubernetesClient.pods().inNamespace(ref.getNamespace())
          .withName(podName).portForward(ref.getPort(), ref.getLocalPort()));
    }

    additionalCustomResourceDefinitions
        .forEach(cr -> applyCrd(ReconcilerUtils.getResourceTypeName(cr)));

    for (var ref : reconcilers) {
      final var config = configurationService.getConfigurationFor(ref.reconciler);
      final var oconfig = override(config).settingNamespace(namespace);

      if (ref.retry != null) {
        oconfig.withRetry(ref.retry);
      }
      if (ref.controllerConfigurationOverrider != null) {
        ref.controllerConfigurationOverrider.accept(oconfig);
      }

      applyCrd(config.getResourceTypeName());

      if (ref.reconciler instanceof KubernetesClientAware) {
        ((KubernetesClientAware) ref.reconciler).setKubernetesClient(kubernetesClient);
      }

      var registeredController = this.operator.register(ref.reconciler, oconfig.build());
      registeredControllers.put(ref.reconciler, registeredController);
    }

    LOGGER.debug("Starting the operator locally");
    this.operator.start();
  }

  private void applyCrd(String resourceTypeName) {
    String path = "/META-INF/fabric8/" + resourceTypeName + "-v1.yml";
    try (InputStream is = getClass().getResourceAsStream(path)) {
      final var crd = getKubernetesClient().load(is);
      crd.createOrReplace();
      Thread.sleep(CRD_READY_WAIT); // readiness is not applicable for CRD, just wait a little
      LOGGER.debug("Applied CRD with path: {}", path);
    } catch (InterruptedException ex) {
      LOGGER.error("Interrupted.", ex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot apply CRD yaml: " + path, ex);
    }
  }

  @Override
  protected void after(ExtensionContext context) {
    super.after(context);

    try {
      this.operator.stop();
    } catch (Exception e) {
      // ignored
    }

    for (var ref : localPortForwards) {
      try {
        ref.close();
      } catch (Exception e) {
        // ignored
      }
    }
    localPortForwards.clear();
  }

  @SuppressWarnings("rawtypes")
  public static class Builder extends AbstractBuilder<Builder> {
    private final List<ReconcilerSpec> reconcilers;
    private final List<PortForwardSpec> portForwards;
    private final List<Class<? extends CustomResource>> additionalCustomResourceDefinitions;

    protected Builder() {
      super();
      this.reconcilers = new ArrayList<>();
      this.portForwards = new ArrayList<>();
      this.additionalCustomResourceDefinitions = new ArrayList<>();
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

    public Builder withPortForward(String namespace, String labelKey, String labelValue, int port,
        int localPort) {
      portForwards.add(new PortForwardSpec(namespace, labelKey, labelValue, port, localPort));
      return this;
    }


    public Builder withAdditionalCustomResourceDefinition(
        Class<? extends CustomResource> customResource) {
      additionalCustomResourceDefinitions.add(customResource);
      return this;
    }

    public LocallyRunOperatorExtension build() {
      return new LocallyRunOperatorExtension(
          configurationService,
          reconcilers,
          infrastructure,
          portForwards,
          additionalCustomResourceDefinitions,
          infrastructureTimeout,
          preserveNamespaceOnError,
          waitForNamespaceDeletion,
          oneNamespacePerClass);
    }
  }

  private static class PortForwardSpec {
    final String namespace;
    final String labelKey;
    final String labelValue;
    final int port;
    final int localPort;

    public PortForwardSpec(String namespace, String labelKey, String labelValue, int port,
        int localPort) {
      this.namespace = namespace;
      this.labelKey = labelKey;
      this.labelValue = labelValue;
      this.port = port;
      this.localPort = localPort;
    }

    public String getNamespace() {
      return namespace;
    }

    public String getLabelKey() {
      return labelKey;
    }

    public String getLabelValue() {
      return labelValue;
    }

    public int getPort() {
      return port;
    }

    public int getLocalPort() {
      return localPort;
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
