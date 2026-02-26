/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.junit;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
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
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.RegisteredController;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider.override;

@SuppressWarnings("rawtypes")
public class LocallyRunOperatorExtension extends AbstractOperatorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocallyRunOperatorExtension.class);
  private static final int CRD_DELETE_TIMEOUT = 5000;
  private static final Set<AppliedCRD> appliedCRDs = new HashSet<>();
  private static final boolean deleteCRDs =
      Boolean.parseBoolean(System.getProperty("testsuite.deleteCRDs", "true"));

  private final Operator operator;
  private final List<ReconcilerSpec> reconcilers;
  private final List<PortForwardSpec> portForwards;
  private final List<LocalPortForward> localPortForwards;
  private final List<Class<? extends CustomResource>> additionalCustomResourceDefinitions;
  private final List<CustomResourceDefinition> additionalCustomResourceDefinitionInstances;
  private final Map<Reconciler, RegisteredController> registeredControllers;
  private final Map<String, String> crdMappings;
  private final Consumer<LocallyRunOperatorExtension> beforeStartHook;

  private LocallyRunOperatorExtension(
      List<ReconcilerSpec> reconcilers,
      List<HasMetadata> infrastructure,
      List<PortForwardSpec> portForwards,
      List<Class<? extends CustomResource>> additionalCustomResourceDefinitions,
      List<CustomResourceDefinition> additionalCustomResourceDefinitionInstances,
      Duration infrastructureTimeout,
      boolean preserveNamespaceOnError,
      boolean skipNamespaceDeletion,
      boolean waitForNamespaceDeletion,
      boolean oneNamespacePerClass,
      KubernetesClient kubernetesClient,
      KubernetesClient infrastructureKubernetesClient,
      Consumer<ConfigurationServiceOverrider> configurationServiceOverrider,
      Function<ExtensionContext, String> namespaceNameSupplier,
      Function<ExtensionContext, String> perClassNamespaceNameSupplier,
      List<String> additionalCrds,
      Consumer<LocallyRunOperatorExtension> beforeStartHook) {
    super(
        infrastructure,
        infrastructureTimeout,
        oneNamespacePerClass,
        preserveNamespaceOnError,
        skipNamespaceDeletion,
        waitForNamespaceDeletion,
        kubernetesClient,
        infrastructureKubernetesClient,
        namespaceNameSupplier,
        perClassNamespaceNameSupplier);
    this.reconcilers = reconcilers;
    this.portForwards = portForwards;
    this.localPortForwards = new ArrayList<>(portForwards.size());
    this.additionalCustomResourceDefinitions = additionalCustomResourceDefinitions;
    this.additionalCustomResourceDefinitionInstances = additionalCustomResourceDefinitionInstances;
    this.beforeStartHook = beforeStartHook;
    configurationServiceOverrider =
        configurationServiceOverrider != null
            ? configurationServiceOverrider.andThen(
                overrider -> overrider.withKubernetesClient(kubernetesClient))
            : overrider -> overrider.withKubernetesClient(kubernetesClient);
    this.operator = new Operator(configurationServiceOverrider);
    this.registeredControllers = new HashMap<>();
    crdMappings = getAdditionalCRDsFromFiles(additionalCrds, getKubernetesClient());
  }

  static Map<String, String> getAdditionalCRDsFromFiles(
      Iterable<String> additionalCrds, KubernetesClient client) {
    Map<String, String> crdMappings = new HashMap<>();
    additionalCrds.forEach(
        p -> {
          try (InputStream is = new FileInputStream(p)) {
            client.load(is).items().stream()
                // only consider CRDs to avoid applying random resources to the cluster
                .filter(CustomResourceDefinition.class::isInstance)
                .map(CustomResourceDefinition.class::cast)
                .forEach(crd -> crdMappings.put(crd.getMetadata().getName(), p));
          } catch (Exception e) {
            throw new RuntimeException("Couldn't load CRD at " + p, e);
          }
        });
    return crdMappings;
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
    applyCrd(ReconcilerUtilsInternal.getResourceTypeName(resourceClass), client);
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
      if (is == null) {
        throw new IllegalStateException("Cannot find CRD at " + path);
      }
      var crdString = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      applyCrd(crdString, path, client);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot apply CRD yaml: " + path, e);
    }
  }

  private static void applyCrd(String crdString, String path, KubernetesClient client) {
    try {
      LOGGER.debug("Applying CRD: {}", crdString);
      final var crd = client.load(new ByteArrayInputStream(crdString.getBytes()));
      crd.serverSideApply();
      appliedCRDs.add(new AppliedCRD.FileCRD(crdString, path));
      Thread.sleep(CRD_READY_WAIT); // readiness is not applicable for CRD, just wait a little
      LOGGER.debug("Applied CRD with path: {}", path);
    } catch (InterruptedException ex) {
      LOGGER.error("Interrupted.", ex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot apply CRD yaml: " + path, ex);
    }
  }

  /**
   * Applies the CRD associated with the specified custom resource, first checking if a CRD has been
   * manually specified using {@link Builder#withAdditionalCRD}, otherwise assuming that its CRD
   * should be found in the standard location as explained in {@link
   * LocallyRunOperatorExtension#applyCrd(String, KubernetesClient)}
   *
   * @param crClass the custom resource class for which we want to apply the CRD
   */
  public void applyCrd(Class<? extends CustomResource> crClass) {
    applyCrd(ReconcilerUtilsInternal.getResourceTypeName(crClass));
  }

  public void applyCrd(CustomResourceDefinition customResourceDefinition) {
    try {
      String resourceTypeName = customResourceDefinition.getMetadata().getName();
      final var pathAsString = crdMappings.get(resourceTypeName);
      if (pathAsString != null) {
        applyCrdFromMappings(pathAsString, resourceTypeName);
      } else {
        var resource = getKubernetesClient().resource(customResourceDefinition);
        resource.serverSideApply();
        Thread.sleep(CRD_READY_WAIT); // readiness is not applicable for CRD, just wait a little
        appliedCRDs.add(new AppliedCRD.InstanceCRD(customResourceDefinition));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void applyCrdFromMappings(String pathAsString, String resourceTypeName) {
    final var path = Path.of(pathAsString);
    try {
      applyCrd(Files.readString(path), pathAsString, getKubernetesClient());
    } catch (IOException e) {
      throw new IllegalStateException("Cannot open CRD file at " + path.toAbsolutePath(), e);
    }
    crdMappings.remove(resourceTypeName);
  }

  /**
   * Applies the CRD associated with the specified resource type name, first checking if a CRD has
   * been manually specified using {@link Builder#withAdditionalCRD}, otherwise assuming that its
   * CRD should be found in the standard location as explained in {@link
   * LocallyRunOperatorExtension#applyCrd(String, KubernetesClient)}
   *
   * @param resourceTypeName the resource type name associated with the CRD to be applied,
   *     typically, given a resource type, its name would be obtained using {@link
   *     ReconcilerUtilsInternal#getResourceTypeName(Class)}
   */
  public void applyCrd(String resourceTypeName) {
    // first attempt to use a manually defined CRD
    final var pathAsString = crdMappings.get(resourceTypeName);
    if (pathAsString != null) {
      applyCrdFromMappings(pathAsString, resourceTypeName);
    } else {
      // if no manually defined CRD matches the resource type, apply the generated one
      applyCrd(resourceTypeName, getKubernetesClient());
    }
  }

  private Stream<Reconciler> reconcilers() {
    return reconcilers.stream().map(reconcilerSpec -> reconcilerSpec.reconciler);
  }

  public List<Reconciler> getReconcilers() {
    return reconcilers().toList();
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

    final var kubernetesClient = getInfrastructureKubernetesClient();

    for (var ref : portForwards) {
      String podName =
          kubernetesClient
              .pods()
              .inNamespace(ref.getNamespace())
              .withLabel(ref.getLabelKey(), ref.getLabelValue())
              .list()
              .getItems()
              .get(0)
              .getMetadata()
              .getName();

      localPortForwards.add(
          kubernetesClient
              .pods()
              .inNamespace(ref.getNamespace())
              .withName(podName)
              .portForward(ref.getPort(), ref.getLocalPort()));
    }

    additionalCustomResourceDefinitions.forEach(this::applyCrd);
    additionalCustomResourceDefinitionInstances.forEach(this::applyCrd);
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

      final var resourceTypeName = ReconcilerUtilsInternal.getResourceTypeName(resourceClass);
      // only try to apply a CRD for the reconciler if it is associated to a CR
      if (CustomResource.class.isAssignableFrom(resourceClass)) {
        applyCrd(resourceTypeName);
      }

      // apply yet unapplied CRDs
      var registeredController = this.operator.register(ref.reconciler, oconfig.build());
      registeredControllers.put(ref.reconciler, registeredController);
    }
    crdMappings.forEach(
        (crdName, path) -> {
          final String crdString;
          try {
            crdString = Files.readString(Path.of(path));
          } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read CRD located at " + path, e);
          }
          applyCrd(crdString, path, getKubernetesClient());
        });
    crdMappings.clear();

    if (beforeStartHook != null) {
      beforeStartHook.accept(this);
    }

    LOGGER.debug("Starting the operator locally");
    this.operator.start();
  }

  @Override
  protected void after(ExtensionContext context) {
    super.after(context);

    var kubernetesClient = getInfrastructureKubernetesClient();

    var iterator = appliedCRDs.iterator();
    while (iterator.hasNext()) {
      deleteCrd(iterator.next(), kubernetesClient);
      iterator.remove();
    }

    // if the client is used for infra client, we should not close it
    // either test or operator should close this client
    if (getKubernetesClient() != getInfrastructureKubernetesClient()) {
      kubernetesClient.close();
    }

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

  private void deleteCrd(AppliedCRD appliedCRD, KubernetesClient client) {
    if (!deleteCRDs) {
      LOGGER.debug("Skipping deleting CRD because of configuration: {}", appliedCRD);
      return;
    }
    appliedCRD.delete(client);
  }

  private sealed interface AppliedCRD permits AppliedCRD.FileCRD, AppliedCRD.InstanceCRD {
    /**
     * Delete this CRD from the cluster
     *
     * @param client client to use for deletion
     */
    void delete(KubernetesClient client);

    record FileCRD(String crdString, String path) implements AppliedCRD {

      @Override
      public void delete(KubernetesClient client) {
        try {
          LOGGER.debug("Deleting CRD: {}", crdString);
          final var crd = client.load(new ByteArrayInputStream(crdString.getBytes()));
          crd.withTimeoutInMillis(CRD_DELETE_TIMEOUT).delete();
          LOGGER.debug("Deleted CRD with path: {}", path);
        } catch (Exception ex) {
          LOGGER.warn(
              "Cannot delete CRD yaml: {}. You might need to delete it manually.", path, ex);
        }
      }
    }

    record InstanceCRD(CustomResourceDefinition customResourceDefinition) implements AppliedCRD {

      @Override
      public void delete(KubernetesClient client) {
        String type = customResourceDefinition.getMetadata().getName();
        try {
          LOGGER.debug("Deleting CustomResourceDefinition instance CRD: {}", type);
          final var crd = client.resource(customResourceDefinition);
          crd.withTimeoutInMillis(CRD_DELETE_TIMEOUT).delete();
          LOGGER.debug("Deleted CustomResourceDefinition instance CRD: {}", type);
        } catch (Exception ex) {
          LOGGER.warn(
              "Cannot delete CustomResourceDefinition instance CRD: {}. You might need to delete it"
                  + " manually.",
              type,
              ex);
        }
      }
    }
  }

  @SuppressWarnings("rawtypes")
  public static class Builder extends AbstractBuilder<Builder> {
    private final List<ReconcilerSpec> reconcilers;
    private final List<PortForwardSpec> portForwards;
    private final List<Class<? extends CustomResource>> additionalCustomResourceDefinitions;
    private final List<CustomResourceDefinition> additionalCustomResourceDefinitionInstances;
    private final List<String> additionalCRDs = new ArrayList<>();
    private Consumer<LocallyRunOperatorExtension> beforeStartHook;
    private KubernetesClient kubernetesClient;
    private KubernetesClient infrastructureKubernetesClient;

    protected Builder() {
      super();
      this.reconcilers = new ArrayList<>();
      this.portForwards = new ArrayList<>();
      this.additionalCustomResourceDefinitions = new ArrayList<>();
      this.additionalCustomResourceDefinitionInstances = new ArrayList<>();
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

    public Builder withPortForward(
        String namespace, String labelKey, String labelValue, int port, int localPort) {
      portForwards.add(new PortForwardSpec(namespace, labelKey, labelValue, port, localPort));
      return this;
    }

    public Builder withKubernetesClient(KubernetesClient kubernetesClient) {
      this.kubernetesClient = kubernetesClient;
      return this;
    }

    public Builder withInfrastructureKubernetesClient(
        KubernetesClient infrastructureKubernetesClient) {
      this.infrastructureKubernetesClient = infrastructureKubernetesClient;
      return this;
    }

    public Builder withAdditionalCustomResourceDefinition(
        Class<? extends CustomResource> customResource) {
      additionalCustomResourceDefinitions.add(customResource);
      return this;
    }

    public Builder withAdditionalCustomResourceDefinition(CustomResourceDefinition definition) {
      additionalCustomResourceDefinitionInstances.add(definition);
      return this;
    }

    public Builder withAdditionalCRD(String... paths) {
      if (paths != null) {
        additionalCRDs.addAll(List.of(paths));
      }
      return this;
    }

    /**
     * Used to initialize resources when the namespace is generated but the operator is not started
     * yet.
     */
    public Builder withBeforeStartHook(Consumer<LocallyRunOperatorExtension> beforeStartHook) {
      this.beforeStartHook = beforeStartHook;
      return this;
    }

    public LocallyRunOperatorExtension build() {
      return new LocallyRunOperatorExtension(
          reconcilers,
          infrastructure,
          portForwards,
          additionalCustomResourceDefinitions,
          additionalCustomResourceDefinitionInstances,
          infrastructureTimeout,
          preserveNamespaceOnError,
          skipNamespaceDeletion,
          waitForNamespaceDeletion,
          oneNamespacePerClass,
          kubernetesClient,
          infrastructureKubernetesClient,
          configurationServiceOverrider,
          namespaceNameSupplier,
          perClassNamespaceNameSupplier,
          additionalCRDs,
          beforeStartHook);
    }
  }

  private static class PortForwardSpec {
    final String namespace;
    final String labelKey;
    final String labelValue;
    final int port;
    final int localPort;

    public PortForwardSpec(
        String namespace, String labelKey, String labelValue, int port, int localPort) {
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
