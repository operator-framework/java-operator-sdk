package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DependentResourceSpec<R, P extends HasMetadata, C> {

  private final Class<? extends DependentResource<R, P>> dependentResourceClass;
  private final String name;
  private final Set<String> dependsOn;
  private final Condition<?, ?> readyCondition;
  private final Condition<?, ?> reconcileCondition;
  private final Condition<?, ?> deletePostCondition;
  private final Condition<?, ?> activationCondition;
  private final String useEventSourceWithName;
  private C nullableConfiguration;

  public DependentResourceSpec(
      Class<? extends DependentResource<R, P>> dependentResourceClass,
      String name,
      Set<String> dependsOn,
      Condition<?, ?> readyCondition,
      Condition<?, ?> reconcileCondition,
      Condition<?, ?> deletePostCondition,
      Condition<?, ?> activationCondition,
      String useEventSourceWithName) {
    this.dependentResourceClass = dependentResourceClass;
    this.name = name;
    this.dependsOn = dependsOn;
    this.readyCondition = readyCondition;
    this.reconcileCondition = reconcileCondition;
    this.deletePostCondition = deletePostCondition;
    this.activationCondition = activationCondition;
    this.useEventSourceWithName = useEventSourceWithName;
  }

  public Class<? extends DependentResource<R, P>> getDependentResourceClass() {
    return dependentResourceClass;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "DependentResourceSpec{ name='"
        + name
        + "', type="
        + getDependentResourceClass().getCanonicalName()
        + '}';
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

  @SuppressWarnings("rawtypes")
  public Condition getActivationCondition() {
    return activationCondition;
  }

  public Optional<String> getUseEventSourceWithName() {
    return Optional.ofNullable(useEventSourceWithName);
  }

  public Optional<C> getConfiguration() {
    return Optional.ofNullable(nullableConfiguration);
  }

  protected void setNullableConfiguration(C configuration) {
    this.nullableConfiguration = configuration;
  }
}
