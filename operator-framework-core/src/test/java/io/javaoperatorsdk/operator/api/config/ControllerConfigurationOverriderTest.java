package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerConfigurationOverriderTest {

  @Test
  void replaceNamedDependentResourceConfigShouldWork() {
    var configuration = new AnnotationControllerConfiguration<>(new OneDepReconciler());
    var dependents = configuration.getDependentResources();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    final var dependentResourceName = DependentResource.defaultNameFor(ReadOnlyDependent.class);
    assertTrue(dependents.containsKey(dependentResourceName));
    var dependentSpec = dependents.get(dependentResourceName);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    var maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);
    var config = (KubernetesDependentResourceConfig) maybeConfig.orElseThrow();
    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().length);
    assertNull(config.labelSelector());
    assertEquals(OneDepReconciler.CONFIGURED_NS, config.namespaces()[0]);

    // override the namespaces for the dependent resource
    final var overriddenNS = "newNS";
    final var labelSelector = "foo=bar";
    final var overridden = ControllerConfigurationOverrider.override(configuration)
        .replaceNamedDependentResourceConfig(
            DependentResource.defaultNameFor(ReadOnlyDependent.class),
            new KubernetesDependentResourceConfig(true, new String[] {overriddenNS}, labelSelector))
        .build();
    dependents = overridden.getDependentResources();
    dependentSpec = dependents.get(dependentResourceName);
    config = (KubernetesDependentResourceConfig) dependentSpec.getDependentResourceConfiguration()
        .orElseThrow();
    assertEquals(1, config.namespaces().length);
    assertEquals(labelSelector, config.labelSelector());
    assertEquals(overriddenNS, config.namespaces()[0]);
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

}
