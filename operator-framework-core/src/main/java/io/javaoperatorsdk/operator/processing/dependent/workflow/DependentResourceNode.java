package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
class DependentResourceNode<R, P extends HasMetadata> {

  private final List<DependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DependentResourceNode> parents = new LinkedList<>();

  private ConditionWithType<R, P, ?> reconcilePrecondition;
  private ConditionWithType<R, P, ?> deletePostcondition;
  private ConditionWithType<R, P, ?> readyPostcondition;
  private ConditionWithType<R, P, ?> activationCondition;
  protected DependentResource<R, P> dependentResource;

  protected DependentResourceNode(Condition<R, P> reconcilePrecondition,
      Condition<R, P> deletePostcondition, Condition<R, P> readyPostcondition,
      Condition<R, P> activationCondition) {
    setReconcilePrecondition(reconcilePrecondition);
    setDeletePostcondition(deletePostcondition);
    setReadyPostcondition(readyPostcondition);
    setActivationCondition(activationCondition);
  }

  DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(null, null, null, null);
    this.dependentResource = dependentResource;
  }

  public List<DependentResourceNode> getDependsOn() {
    return dependsOn;
  }

  void addParent(DependentResourceNode parent) {
    parents.add(parent);
  }

  void addDependsOnRelation(DependentResourceNode node) {
    node.addParent(this);
    dependsOn.add(node);
  }

  public List<DependentResourceNode> getParents() {
    return parents;
  }

  public Optional<ConditionWithType<R, P, ?>> getReconcilePrecondition() {
    return Optional.ofNullable(reconcilePrecondition);
  }

  void setReconcilePrecondition(Condition<R, P> reconcilePrecondition) {
    this.reconcilePrecondition = reconcilePrecondition == null ? null
        : new ConditionWithType<>(reconcilePrecondition, Condition.Type.RECONCILE);
  }

  public Optional<ConditionWithType<R, P, ?>> getDeletePostcondition() {
    return Optional.ofNullable(deletePostcondition);
  }

  void setDeletePostcondition(Condition<R, P> deletePostcondition) {
    this.deletePostcondition = deletePostcondition == null ? null
        : new ConditionWithType<>(deletePostcondition, Condition.Type.DELETE);
  }

  public Optional<ConditionWithType<R, P, ?>> getActivationCondition() {
    return Optional.ofNullable(activationCondition);
  }

  void setActivationCondition(Condition<R, P> activationCondition) {
    this.activationCondition = activationCondition == null ? null
        : new ConditionWithType<>(activationCondition, Condition.Type.ACTIVATION);
  }

  public Optional<ConditionWithType<R, P, ?>> getReadyPostcondition() {
    return Optional.ofNullable(readyPostcondition);
  }

  void setReadyPostcondition(Condition<R, P> readyPostcondition) {
    this.readyPostcondition = readyPostcondition == null ? null
        : new ConditionWithType<>(readyPostcondition, Condition.Type.READY);
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  public String name() {
    return getDependentResource().name();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DependentResourceNode<?, ?> that = (DependentResourceNode<?, ?>) o;
    return name().equals(that.name());
  }

  @Override
  public int hashCode() {
    return name().hashCode();
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" + getDependentResource() + '}';
  }
}
