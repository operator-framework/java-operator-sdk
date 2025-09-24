package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class DependentResourceNode<R, P extends HasMetadata> {

  private final List<DependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DependentResourceNode> parents = new LinkedList<>();

  private ConditionWithType<R, P, ?> reconcilePrecondition;
  private ConditionWithType<R, P, ?> deletePostcondition;
  private ConditionWithType<R, P, ?> readyPostcondition;
  private ConditionWithType<R, P, ?> activationCondition;
  private final DependentResource<R, P> dependentResource;

  DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(null, null, null, null, dependentResource);
  }

  public DependentResourceNode(
      Condition<R, P> reconcilePrecondition,
      Condition<R, P> deletePostcondition,
      Condition<R, P> readyPostcondition,
      Condition<R, P> activationCondition,
      DependentResource<R, P> dependentResource) {
    setReconcilePrecondition(reconcilePrecondition);
    setDeletePostcondition(deletePostcondition);
    setReadyPostcondition(readyPostcondition);
    setActivationCondition(activationCondition);
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

  public Optional<ConditionWithType<R, P, ?>> getDeletePostcondition() {
    return Optional.ofNullable(deletePostcondition);
  }

  public Optional<ConditionWithType<R, P, ?>> getActivationCondition() {
    return Optional.ofNullable(activationCondition);
  }

  public Optional<ConditionWithType<R, P, ?>> getReadyPostcondition() {
    return Optional.ofNullable(readyPostcondition);
  }

  void setReconcilePrecondition(Condition<R, P> reconcilePrecondition) {
    this.reconcilePrecondition =
        reconcilePrecondition == null
            ? null
            : new ConditionWithType<>(reconcilePrecondition, Condition.Type.RECONCILE);
  }

  void setDeletePostcondition(Condition<R, P> deletePostcondition) {
    this.deletePostcondition =
        deletePostcondition == null
            ? null
            : new ConditionWithType<>(deletePostcondition, Condition.Type.DELETE);
  }

  void setActivationCondition(Condition<R, P> activationCondition) {
    this.activationCondition =
        activationCondition == null
            ? null
            : new ConditionWithType<>(activationCondition, Condition.Type.ACTIVATION);
  }

  void setReadyPostcondition(Condition<R, P> readyPostcondition) {
    this.readyPostcondition =
        readyPostcondition == null
            ? null
            : new ConditionWithType<>(readyPostcondition, Condition.Type.READY);
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
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
    return this.getDependentResource().name().equals(that.getDependentResource().name());
  }

  @Override
  public int hashCode() {
    return this.getDependentResource().name().hashCode();
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" + getDependentResource() + '}';
  }
}
