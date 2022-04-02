package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerConfigurationOverriderTest {

  @Test
  void configuredDependentShouldNotChangeOnParentOverrideEvenWhenInitialConfigIsSame() {
    io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration =
        new AnnotationControllerConfiguration<>(new OverriddenNSOnDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();

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
    config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.namespaces());
  }

  @Test
  void dependentShouldWatchAllNamespacesIfParentDoesAsWell() {
    io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration =
        new AnnotationControllerConfiguration<>(new WatchAllNamespacesReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();

    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(0, config.namespaces().size());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is using the overridden namespace
    config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(newNS), config.namespaces());
  }

  @Test
  void shouldBePossibleToForceDependentToWatchAllNamespaces() {
    io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration =
        new AnnotationControllerConfiguration<>(new DependentWatchesAllNSReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();

    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(0, config.namespaces().size());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still configured to watch all NS
    config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();
    assertEquals(0, config.namespaces().size());
  }

  @Test
  void overridingNamespacesShouldBePropagatedToDependentsWithDefaultConfig() {
    io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration =
        new AnnotationControllerConfiguration<>(new OneDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();

    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OneDepReconciler.CONFIGURED_NS), config.namespaces());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is using the overridden namespace
    config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(newNS), config.namespaces());
  }

  @Test
  void alreadyOverriddenDependentNamespacesShouldNotBePropagated() {
    io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration =
        new AnnotationControllerConfiguration<>(new OverriddenNSOnDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();

    // DependentResource has its own NS
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.namespaces());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still using its own NS
    config = (KubernetesDependentResourceConfig) configuration.getDependentResources().get(0)
        .getDependentResourceConfiguration().orElseThrow();
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.namespaces());
  }

  @Test
  void replaceNamedDependentResourceConfigShouldWork() {
    var configuration = new AnnotationControllerConfiguration<>(new OneDepReconciler());
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
