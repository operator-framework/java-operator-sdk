package io.javaoperatorsdk.operator.config.runtime;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.sample.readonly.ReadOnlyDependent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationControllerConfigurationTest {

  @Test
  void getDependentResources() {
    var configuration = new AnnotationControllerConfiguration<>(new NoDepReconciler());
    var dependents = configuration.getDependentResources();
    assertTrue(dependents.isEmpty());

    configuration = new AnnotationControllerConfiguration<>(new OneDepReconciler());
    dependents = configuration.getDependentResources();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    final var dependentResourceName = DependentResource.defaultNameFor(ReadOnlyDependent.class);
    assertTrue(dependents.containsKey(dependentResourceName));
    var dependentSpec = dependents.get(dependentResourceName);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    var maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);
    final var config = (KubernetesDependentResourceConfig) maybeConfig.orElseThrow();
    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().length);
    assertEquals(OneDepReconciler.CONFIGURED_NS, config.namespaces()[0]);

    configuration = new AnnotationControllerConfiguration<>(new NamedDepReconciler());
    dependents = configuration.getDependentResources();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    dependentSpec = dependents.get(NamedDepReconciler.NAME);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);
  }

  @Test
  void tryingToAddDuplicatedDependentsWithoutNameShouldFail() {
    var configuration = new AnnotationControllerConfiguration<>(new DuplicatedDepReconciler());
    assertThrows(IllegalArgumentException.class, configuration::getDependentResources);
  }

  @Test
  void addingDuplicatedDependentsWithNameShouldWork() {
    var config = new AnnotationControllerConfiguration<>(new NamedDuplicatedDepReconciler());
    var dependents = config.getDependentResources();
    assertEquals(2, dependents.size());
    assertTrue(dependents.containsKey(NamedDuplicatedDepReconciler.NAME)
        && dependents.containsKey(DependentResource.defaultNameFor(ReadOnlyDependent.class)));
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

  @ControllerConfiguration(
      dependents = @Dependent(type = ReadOnlyDependent.class, name = NamedDepReconciler.NAME))
  private static class NamedDepReconciler implements Reconciler<ConfigMap> {
    private static final String NAME = "foo";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(
      dependents = {
          @Dependent(type = ReadOnlyDependent.class),
          @Dependent(type = ReadOnlyDependent.class)
      })
  private static class DuplicatedDepReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(
      dependents = {
          @Dependent(type = ReadOnlyDependent.class, name = NamedDuplicatedDepReconciler.NAME),
          @Dependent(type = ReadOnlyDependent.class)
      })
  private static class NamedDuplicatedDepReconciler implements Reconciler<ConfigMap> {

    private static final String NAME = "duplicated";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  private static class NoDepReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }
}
