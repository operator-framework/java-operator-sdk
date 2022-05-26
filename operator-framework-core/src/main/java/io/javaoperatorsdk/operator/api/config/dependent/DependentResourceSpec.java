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

  private Set<String> dependsOn;

  private Condition<?,?> readyCondition;

  private Condition<?,?> reconcileCondition;

  private Condition<?,?> deletePostCondition;

  public DependentResourceSpec(Class<T> dependentResourceClass, C dependentResourceConfig,
      String name) {
    this.dependentResourceClass = dependentResourceClass;
    this.dependentResourceConfig = dependentResourceConfig;
    this.name = name;
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

  public DependentResourceSpec<T, C> setDependsOn(Set<String> dependsOn) {
    this.dependsOn = dependsOn;
    return this;
  }

  public Condition<?, ?> getReadyCondition() {
    return readyCondition;
  }

  public DependentResourceSpec<T, C> setReadyCondition(Condition<?, ?> readyCondition) {
    this.readyCondition = readyCondition;
    return this;
  }

  public Condition<?, ?> getReconcileCondition() {
    return reconcileCondition;
  }

  public DependentResourceSpec<T, C> setReconcileCondition(Condition<?, ?> reconcileCondition) {
    this.reconcileCondition = reconcileCondition;
    return this;
  }

  public Condition<?, ?> getDeletePostCondition() {
    return deletePostCondition;
  }

  public DependentResourceSpec<T, C> setDeletePostCondition(Condition<?, ?> deletePostCondition) {
    this.deletePostCondition = deletePostCondition;
    return this;
  }
}
