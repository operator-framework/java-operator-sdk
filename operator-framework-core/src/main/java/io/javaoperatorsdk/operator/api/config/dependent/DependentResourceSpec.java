package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DependentResourceSpec<R, P extends HasMetadata, C> {

  private final DependentResource<R, P> dependentResource;

  private final String name;

  private final Set<String> dependsOn;

  private final Condition<?, ?> readyCondition;

  private final Condition<?, ?> reconcileCondition;

  private final Condition<?, ?> deletePostCondition;

  public DependentResourceSpec(DependentResource<R, P> dependentResource,
      String name, Set<String> dependsOn, Condition<?, ?> readyCondition,
      Condition<?, ?> reconcileCondition, Condition<?, ?> deletePostCondition) {
    this.dependentResource = dependentResource;
    this.name = name;
    this.dependsOn = dependsOn;
    this.readyCondition = readyCondition;
    this.reconcileCondition = reconcileCondition;
    this.deletePostCondition = deletePostCondition;
  }

  @SuppressWarnings("unchecked")
  public Class<DependentResource<R, P>> getDependentResourceClass() {
    return (Class<DependentResource<R, P>>) dependentResource.getClass();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Optional<C> getDependentResourceConfiguration() {
    if (dependentResource instanceof DependentResourceConfigurator) {
      var configurator = (DependentResourceConfigurator) dependentResource;
      return configurator.configuration();
    }
    return Optional.empty();
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "DependentResourceSpec{ name='" + name +
        "', type=" + getDependentResourceClass().getCanonicalName() + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DependentResourceSpec<?, ?, ?> that = (DependentResourceSpec<?, ?, ?>) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  public Set<String> getDependsOn() {
    return dependsOn;
  }

  @SuppressWarnings("rawtypes")
  public Condition getReadyCondition() {
    return readyCondition;
  }

  @SuppressWarnings("rawtypes")
  public Condition getReconcileCondition() {
    return reconcileCondition;
  }

  @SuppressWarnings("rawtypes")
  public Condition getDeletePostCondition() {
    return deletePostCondition;
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }
}
