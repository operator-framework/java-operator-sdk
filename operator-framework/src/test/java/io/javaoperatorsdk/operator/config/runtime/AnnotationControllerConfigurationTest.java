package io.javaoperatorsdk.operator.config.runtime;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.sample.readonly.ReadOnlyDependent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationControllerConfigurationTest {

  @Test
  void defaultValuesShouldBeConsistent() {
    final var configuration = new AnnotationControllerConfiguration<>(new SelectorReconciler());
    final var annotated = extractDependentKubernetesResourceConfig(configuration, 1);
    final var unannotated = extractDependentKubernetesResourceConfig(configuration, 0);

    assertNull(annotated.labelSelector());
    assertNull(unannotated.labelSelector());
  }

  private KubernetesDependentResourceConfig extractDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    return (KubernetesDependentResourceConfig) configuration.getDependentResources().get(index)
        .getDependentResourceConfiguration()
        .orElseThrow();
  }

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
    assertTrue(dependents.stream().anyMatch(d -> d.getName().equals(dependentResourceName)));
    var dependentSpec = findByName(dependents, dependentResourceName);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    var maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);
    final var config = (KubernetesDependentResourceConfig) maybeConfig.orElseThrow();
    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OneDepReconciler.CONFIGURED_NS), config.namespaces());

    configuration = new AnnotationControllerConfiguration<>(new NamedDepReconciler());
    dependents = configuration.getDependentResources();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    dependentSpec = findByName(dependents, NamedDepReconciler.NAME);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);
  }

  @Test
  void missingAnnotationThrowsException() {
    Assertions.assertThrows(OperatorException.class, () -> {
      new AnnotationControllerConfiguration<>(new MissingAnnotationReconciler());
    });
  }

  @SuppressWarnings("rawtypes")
  private DependentResourceSpec findByName(
      List<DependentResourceSpec> dependentResourceSpecList, String name) {
    return dependentResourceSpecList.stream().filter(d -> d.getName().equals(name)).findFirst()
        .get();
  }

  @SuppressWarnings("rawtypes")
  private Optional<DependentResourceSpec> findByNameOptional(
      List<DependentResourceSpec> dependentResourceSpecList, String name) {
    return dependentResourceSpecList.stream().filter(d -> d.getName().equals(name)).findFirst();
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
    assertTrue(findByNameOptional(dependents, NamedDuplicatedDepReconciler.NAME).isPresent()
        && findByNameOptional(dependents, DependentResource.defaultNameFor(ReadOnlyDependent.class))
            .isPresent());
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

  @ControllerConfiguration
  private static class NoDepReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(dependents = {
      @Dependent(type = SelectorReconciler.WithAnnotation.class),
      @Dependent(type = ReadOnlyDependent.class)
  })
  private static class SelectorReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return null;
    }

    @KubernetesDependent
    private static class WithAnnotation extends KubernetesDependentResource<ConfigMap, ConfigMap> {

      public WithAnnotation() {
        super(ConfigMap.class);
      }
    }
  }

  private static class MissingAnnotationReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }
}
