package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DependentResourceSpec<T extends DependentResource<?, ?>, C> {

  private final Class<T> dependentResourceClass;

  private final C dependentResourceConfig;

  private final String name;

  private final Set<String> dependsOn;

  private final Condition<?, ?> readyCondition;

  private final Condition<?, ?> reconcileCondition;

  private final Condition<?, ?> deletePostCondition;

  public DependentResourceSpec(Class<T> dependentResourceClass, C dependentResourceConfig,
      String name, Set<String> dependsOn, Condition<?, ?> readyCondition,
      Condition<?, ?> reconcileCondition, Condition<?, ?> deletePostCondition) {
    this.dependentResourceClass = dependentResourceClass;
    this.dependentResourceConfig = dependentResourceConfig;
    this.name = name;
    this.dependsOn = dependsOn;
    this.readyCondition = readyCondition;
    this.reconcileCondition = reconcileCondition;
    this.deletePostCondition = deletePostCondition;
  }

  public Class<T> getDependentResourceClass() {
    return dependentResourceClass;
  }

  public Optional<C> getDependentResourceConfiguration() {
    return Optional.ofNullable(dependentResourceConfig);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "DependentResourceSpec{ name='" + name +
        "', type=" + dependentResourceClass.getCanonicalName() +
        ", config=" + dependentResourceConfig + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DependentResourceSpec<?, ?> that = (DependentResourceSpec<?, ?>) o;
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
}
