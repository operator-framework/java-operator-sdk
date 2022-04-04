package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerConfigurationOverriderTest {

  @Test
  void overridingNSShouldPreserveUntouchedDependents() {
    var configuration = createConfiguration(new NamedDependentReconciler());

    // check that we have the proper number of dependent configs
    var dependentResources = configuration.getDependentResources();
    assertEquals(2, dependentResources.size());

    // override the NS
    final var namespace = "some-ns";
    final var externalDRName =
        DependentResource.defaultNameFor(NamedDependentReconciler.ExternalDependendResource.class);
    final var stringConfig = "some String configuration";
    configuration = ControllerConfigurationOverrider.override(configuration)
        .settingNamespace(namespace)
        .replacingNamedDependentResourceConfig(externalDRName, stringConfig)
        .build();
    assertEquals(Set.of(namespace), configuration.getNamespaces());

    // check that we still have the proper number of dependent configs
    dependentResources = configuration.getDependentResources();
    assertEquals(2, dependentResources.size());
    final var resourceConfig = extractDependentKubernetesResourceConfig(
        configuration, 1);
    assertEquals(stringConfig, resourceConfig);
  }

  @ControllerConfiguration(dependents = {
      @Dependent(type = NamedDependentReconciler.NamedDependentResource.class),
      @Dependent(type = NamedDependentReconciler.ExternalDependendResource.class)
  })
  private static class NamedDependentReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return null;
    }

    private static class NamedDependentResource
        extends KubernetesDependentResource<ConfigMap, ConfigMap> {

      public NamedDependentResource() {
        super(ConfigMap.class);
      }
    }

    private static class ExternalDependendResource implements DependentResource<Object, ConfigMap> {

      @Override
      public ReconcileResult<Object> reconcile(ConfigMap primary, Context<ConfigMap> context) {
        return null;
      }

      @Override
      public Class<Object> resourceType() {
        return Object.class;
      }

      @Override
      public Optional<Object> getSecondaryResource(ConfigMap primary) {
        return Optional.empty();
      }
    }
  }

  private KubernetesDependentResourceConfig extractFirstDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration) {
    return (KubernetesDependentResourceConfig) extractDependentKubernetesResourceConfig(
        configuration, 0);
  }

  private Object extractDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    return configuration.getDependentResources().get(index).getDependentResourceConfiguration()
        .orElseThrow();
  }

  private io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> createConfiguration(
      Reconciler<?> reconciler) {
    return new AnnotationControllerConfiguration<>(reconciler);
  }

  @Test
  void configuredDependentShouldNotChangeOnParentOverrideEvenWhenInitialConfigIsSame() {
    var configuration = createConfiguration(new OverriddenNSOnDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // override the parent NS to match the dependent's
    configuration = ControllerConfigurationOverrider.override(configuration)
        .settingNamespace(OverriddenNSDependent.DEP_NS).build();
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), configuration.getNamespaces());

    // check that the DependentResource inherits has its own configured NS
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.namespaces());

    // override the parent's NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still using its own NS
    config = extractFirstDependentKubernetesResourceConfig(configuration);
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.namespaces());
  }

  @Test
  void dependentShouldWatchAllNamespacesIfParentDoesAsWell() {
    var configuration = createConfiguration(new WatchAllNamespacesReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(0, config.namespaces().size());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is using the overridden namespace
    config = extractFirstDependentKubernetesResourceConfig(configuration);
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(newNS), config.namespaces());
  }

  @Test
  void shouldBePossibleToForceDependentToWatchAllNamespaces() {
    var configuration = createConfiguration(new DependentWatchesAllNSReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(0, config.namespaces().size());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still configured to watch all NS
    config = extractFirstDependentKubernetesResourceConfig(configuration);
    assertEquals(0, config.namespaces().size());
  }

  @Test
  void overridingNamespacesShouldBePropagatedToDependentsWithDefaultConfig() {
    var configuration = createConfiguration(new OneDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OneDepReconciler.CONFIGURED_NS), config.namespaces());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is using the overridden namespace
    config = extractFirstDependentKubernetesResourceConfig(configuration);
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(newNS), config.namespaces());
  }

  @Test
  void alreadyOverriddenDependentNamespacesShouldNotBePropagated() {
    var configuration = createConfiguration(new OverriddenNSOnDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // DependentResource has its own NS
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.namespaces());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still using its own NS
    config = extractFirstDependentKubernetesResourceConfig(configuration);
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.namespaces());
  }

  @Test
  void replaceNamedDependentResourceConfigShouldWork() {
    var configuration = createConfiguration(new OneDepReconciler());
    var dependents = configuration.getDependentResources();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());

    final var dependentResourceName = DependentResource.defaultNameFor(ReadOnlyDependent.class);
    assertTrue(dependents.stream().anyMatch(dr -> dr.getName().equals(dependentResourceName)));

    var dependentSpec = dependents.stream().filter(dr -> dr.getName().equals(dependentResourceName))
        .findFirst().get();
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    var maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);

    var config = (KubernetesDependentResourceConfig) maybeConfig.orElseThrow();
    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().size());
    assertNull(config.labelSelector());
    assertEquals(Set.of(OneDepReconciler.CONFIGURED_NS), config.namespaces());

    // override the namespaces for the dependent resource
    final var overriddenNS = "newNS";
    final var labelSelector = "foo=bar";
    final var overridden = ControllerConfigurationOverrider.override(configuration)
        .replacingNamedDependentResourceConfig(
            DependentResource.defaultNameFor(ReadOnlyDependent.class),
            new KubernetesDependentResourceConfig(Set.of(overriddenNS), labelSelector))
        .build();
    dependents = overridden.getDependentResources();
    dependentSpec = dependents.stream().filter(dr -> dr.getName().equals(dependentResourceName))
        .findFirst().get();
    config = (KubernetesDependentResourceConfig) dependentSpec.getDependentResourceConfiguration()
        .orElseThrow();
    assertEquals(1, config.namespaces().size());
    assertEquals(labelSelector, config.labelSelector());
    assertEquals(Set.of(overriddenNS), config.namespaces());
  }

  @ControllerConfiguration(dependents = @Dependent(type = ReadOnlyDependent.class))
  private static class WatchAllNamespacesReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(dependents = @Dependent(type = WatchAllNSDependent.class))
  private static class DependentWatchesAllNSReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(namespaces = OneDepReconciler.CONFIGURED_NS,
      dependents = @Dependent(type = ReadOnlyDependent.class))
  private static class OneDepReconciler implements Reconciler<ConfigMap> {

    private static final String CONFIGURED_NS = "foo";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  private static class ReadOnlyDependent extends KubernetesDependentResource<ConfigMap, ConfigMap> {

    public ReadOnlyDependent() {
      super(ConfigMap.class);
    }
  }

  @KubernetesDependent(namespaces = KubernetesDependent.WATCH_ALL_NAMESPACES)
  private static class WatchAllNSDependent
      extends KubernetesDependentResource<ConfigMap, ConfigMap> {

    public WatchAllNSDependent() {
      super(ConfigMap.class);
    }
  }

  @ControllerConfiguration(namespaces = OverriddenNSOnDepReconciler.CONFIGURED_NS,
      dependents = @Dependent(type = OverriddenNSDependent.class))
  private static class OverriddenNSOnDepReconciler implements Reconciler<ConfigMap> {

    private static final String CONFIGURED_NS = "parentNS";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @KubernetesDependent(namespaces = OverriddenNSDependent.DEP_NS)
  private static class OverriddenNSDependent
      extends KubernetesDependentResource<ConfigMap, ConfigMap> {

    private static final String DEP_NS = "dependentNS";

    public OverriddenNSDependent() {
      super(ConfigMap.class);
    }
  }
}
