package io.javaoperatorsdk.operator.junit;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.RegisteredController;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
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
  private final List<String> additionalCrds;

  private LocallyRunOperatorExtension(
      List<ReconcilerSpec> reconcilers,
      List<HasMetadata> infrastructure,
      List<PortForwardSpec> portForwards,
      List<Class<? extends CustomResource>> additionalCustomResourceDefinitions,
      Duration infrastructureTimeout,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion,
      boolean oneNamespacePerClass,
      KubernetesClient kubernetesClient,
      Consumer<ConfigurationServiceOverrider> configurationServiceOverrider,
      Function<ExtensionContext, String> namespaceNameSupplier,
      Function<ExtensionContext, String> perClassNamespaceNameSupplier,
      List<String> additionalCrds) {
    super(
        infrastructure,
        infrastructureTimeout,
        oneNamespacePerClass,
        preserveNamespaceOnError,
        waitForNamespaceDeletion,
        kubernetesClient,
        namespaceNameSupplier,
        perClassNamespaceNameSupplier);
    this.reconcilers = reconcilers;
    this.portForwards = portForwards;
    this.localPortForwards = new ArrayList<>(portForwards.size());
    this.additionalCustomResourceDefinitions = additionalCustomResourceDefinitions;
      configurationServiceOverrider = configurationServiceOverrider != null
              ? configurationServiceOverrider
              .andThen(overrider -> overrider.withKubernetesClient(kubernetesClient))
              : overrider -> overrider.withKubernetesClient(kubernetesClient);
    this.operator = new Operator(
        configurationServiceOverrider == null ? o -> o.withKubernetesClient(getKubernetesClient())
            : configurationServiceOverrider
                .andThen(o -> o.withKubernetesClient(getKubernetesClient())));
    this.registeredControllers = new HashMap<>();
    this.additionalCrds = additionalCrds;
  }

  /**
   * Creates a {@link Builder} to set up an {@link LocallyRunOperatorExtension} instance.
   *
   * @return the builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static void applyCrd(Class<? extends HasMetadata> resourceClass, KubernetesClient client) {
    applyCrd(ReconcilerUtils.getResourceTypeName(resourceClass), client);
  }

  /**
   * Applies the CRD associated with the specified resource name to the cluster. Note that the CRD
   * is assumed to have been generated in this case from the Java classes and is therefore expected
   * to be found in the standard location with the default name for such CRDs and assumes a v1
   * version of the CRD spec is used. This means that, provided a given {@code resourceTypeName},
   * the associated CRD is expected to be found at {@code META-INF/fabric8/resourceTypeName-v1.yml}
   * in the project's classpath.
   *
   * @param resourceTypeName the standard resource name for CRDs i.e. {@code plural.group}
   * @param client the kubernetes client to use to connect to the cluster
   */
  public static void applyCrd(String resourceTypeName, KubernetesClient client) {
    String path = "/META-INF/fabric8/" + resourceTypeName + "-v1.yml";
    try (InputStream is = LocallyRunOperatorExtension.class.getResourceAsStream(path)) {
      applyCrd(is, path, client);
    } catch (IllegalStateException e) {
      // rethrow directly
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot apply CRD yaml: " + path, e);
    }
  }

  public static void applyCrd(CustomResourceDefinition crd, KubernetesClient client) {
    client.resource(crd).serverSideApply();
  }

  private static void applyCrd(InputStream is, String path, KubernetesClient client) {
    try {
      if (is == null) {
        throw new IllegalStateException("Cannot find CRD at " + path);
      }
      var crdString = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      LOGGER.debug("Applying CRD: {}", crdString);
      final var crd = client.load(new ByteArrayInputStream(crdString.getBytes()));
      crd.serverSideApply();
      Thread.sleep(CRD_READY_WAIT); // readiness is not applicable for CRD, just wait a little
      LOGGER.debug("Applied CRD with path: {}", path);
    } catch (InterruptedException ex) {
      LOGGER.error("Interrupted.", ex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot apply CRD yaml: " + path, ex);
    }
  }

  public static List<CustomResourceDefinition> parseCrds(String path, KubernetesClient client) {
    try (InputStream is = new FileInputStream(path)) {
      return client.load(new ByteArrayInputStream(is.readAllBytes()))
          .items().stream().map(i -> (CustomResourceDefinition) i).collect(Collectors.toList());
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  public Operator getOperator() {
    return operator;
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

    additionalCustomResourceDefinitions.forEach(this::applyCrd);
    Map<String, CustomResourceDefinition> unappliedCRDs = getAdditionalCRDsFromFiles();
    for (var ref : reconcilers) {
      final var config = operator.getConfigurationService().getConfigurationFor(ref.reconciler);
      final var oconfig = override(config);

      final var resourceClass = config.getResourceClass();
      if (Namespaced.class.isAssignableFrom(resourceClass)) {
        oconfig.settingNamespace(namespace);
      }

      if (ref.retry != null) {
        oconfig.withRetry(ref.retry);
      }
      if (ref.controllerConfigurationOverrider != null) {
        ref.controllerConfigurationOverrider.accept(oconfig);
      }

      final var resourceTypeName = ReconcilerUtils.getResourceTypeName(resourceClass);
      // only try to apply a CRD for the reconciler if it is associated to a CR
      if (CustomResource.class.isAssignableFrom(resourceClass)) {
        if (unappliedCRDs.get(resourceTypeName) != null) {
          applyCrd(resourceTypeName);
          unappliedCRDs.remove(resourceTypeName);
        } else {
          applyCrd(resourceClass);
        }
      }

      // apply yet unapplied CRDs
      var registeredController = this.operator.register(ref.reconciler, oconfig.build());
      registeredControllers.put(ref.reconciler, registeredController);
    }
    unappliedCRDs.keySet().forEach(this::applyCrd);

    LOGGER.debug("Starting the operator locally");
    this.operator.start();
  }

  private Map<String, CustomResourceDefinition> getAdditionalCRDsFromFiles() {
    Map<String, CustomResourceDefinition> crdMappings = new HashMap<>();
    additionalCrds.forEach(p -> {
      var crds = parseCrds(p, getKubernetesClient());
      crds.forEach(c -> crdMappings.put(c.getMetadata().getName(), c));
    });
    return crdMappings;
  }

  /**
   * Applies the CRD associated with the specified custom resource, first checking if a CRD has been
   * manually specified using {@link Builder#withAdditionalCRD(String)}, otherwise assuming that its
   * CRD should be found in the standard location as explained in
   * {@link LocallyRunOperatorExtension#applyCrd(String, KubernetesClient)}
   *
   * @param crClass the custom resource class for which we want to apply the CRD
   */
  public void applyCrd(Class<? extends CustomResource> crClass) {
    applyCrd(ReconcilerUtils.getResourceTypeName(crClass));
  }

  public void applyCrd(String resourceTypeName) {
    applyCrd(resourceTypeName, getKubernetesClient());
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
    private final Map<String, String> crdMappings;
    private final List<String> additionalCRDs = new ArrayList<>();
    private KubernetesClient kubernetesClient;

    protected Builder() {
      super();
      this.reconcilers = new ArrayList<>();
      this.portForwards = new ArrayList<>();
      this.additionalCustomResourceDefinitions = new ArrayList<>();
      this.crdMappings = new HashMap<>();
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

    public Builder withKubernetesClient(KubernetesClient kubernetesClient) {
      this.kubernetesClient = kubernetesClient;
      return this;
    }

    public Builder withAdditionalCustomResourceDefinition(
        Class<? extends CustomResource> customResource) {
      additionalCustomResourceDefinitions.add(customResource);
      return this;
    }

    public Builder withAdditionalCRD(String path) {
      additionalCRDs.add(path);
      return this;
    }

    public LocallyRunOperatorExtension build() {
      return new LocallyRunOperatorExtension(
          reconcilers,
          infrastructure,
          portForwards,
          additionalCustomResourceDefinitions,
          infrastructureTimeout,
          preserveNamespaceOnError,
          waitForNamespaceDeletion,
          oneNamespacePerClass,
          kubernetesClient,
          configurationServiceOverrider, namespaceNameSupplier,
          perClassNamespaceNameSupplier,
          additionalCRDs);
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
