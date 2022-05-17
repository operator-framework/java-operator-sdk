package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DependentResourceNode<R, P extends HasMetadata> {

  private final DependentResource<R, P> dependentResource;
  private Condition reconcileCondition;
  private Condition cleanupCondition;
  private Condition readyCondition;
  private List<DependentResourceNode> dependsOn = new ArrayList<>(1);

  public DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      Condition reconcileCondition) {
    this(dependentResource, reconcileCondition, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      Condition reconcileCondition, Condition cleanupCondition) {
    this.dependentResource = dependentResource;
    this.reconcileCondition = reconcileCondition;
    this.cleanupCondition = cleanupCondition;
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  public Optional<Condition> getReconcileCondition() {
    return Optional.ofNullable(reconcileCondition);
  }

  public Optional<Condition> getDeletePostCondition() {
    return Optional.ofNullable(cleanupCondition);
  }

  public void setDependsOn(List<DependentResourceNode> dependsOn) {
    this.dependsOn = dependsOn;
  }

  public List<DependentResourceNode> getDependsOn() {
    return dependsOn;
  }

  public void addDependsOnRelation(DependentResourceNode node) {
    dependsOn.add(node);
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" +
        "dependentResource=" + dependentResource +
        '}';
  }

  public DependentResourceNode<R, P> setReconcileCondition(
      Condition reconcileCondition) {
    this.reconcileCondition = reconcileCondition;
    return this;
  }

  public DependentResourceNode<R, P> setCleanupCondition(Condition cleanupCondition) {
    this.cleanupCondition = cleanupCondition;
    return this;
  }

  public Optional<Condition<R, P>> getReadyCondition() {
    return Optional.ofNullable(readyCondition);
  }

  public DependentResourceNode<R, P> setReadyCondition(Condition readyCondition) {
    this.readyCondition = readyCondition;
    return this;
  }
}
