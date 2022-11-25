package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class DefaultDependentResourceNode<R, P extends HasMetadata> implements
    DependentResourceNode<R, P> {

  private final DependentResource<R, P> dependentResource;
  private Condition<R, P> reconcilePrecondition;
  private Condition<R, P> deletePostcondition;
  private Condition<R, P> readyPostcondition;
  private final List<DependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DependentResourceNode> parents = new LinkedList<>();

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource,
      Condition<R, P> reconcilePrecondition) {
    this(dependentResource, reconcilePrecondition, null);
  }

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource,
      Condition<R, P> reconcilePrecondition, Condition<R, P> deletePostcondition) {
    this.dependentResource = dependentResource;
    this.reconcilePrecondition = reconcilePrecondition;
    this.deletePostcondition = deletePostcondition;
  }

  @Override
  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  @Override
  public Optional<Condition<R, P>> getReconcilePrecondition() {
    return Optional.ofNullable(reconcilePrecondition);
  }

  @Override
  public Optional<Condition<R, P>> getDeletePostcondition() {
    return Optional.ofNullable(deletePostcondition);
  }

  @Override
  public List<? extends DependentResourceNode> getDependsOn() {
    return dependsOn;
  }

  @Override
  public void addParent(DependentResourceNode parent) {
    parents.add(parent);
  }

  @Override
  public void addDependsOnRelation(DependentResourceNode node) {
    node.addParent(this);
    dependsOn.add(node);
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" + dependentResource + '}';
  }

  public void setReconcilePrecondition(Condition<R, P> reconcilePrecondition) {
    this.reconcilePrecondition = reconcilePrecondition;
  }

  public void setDeletePostcondition(Condition<R, P> cleanupCondition) {
    this.deletePostcondition = cleanupCondition;
  }

  @Override
  public Optional<Condition<R, P>> getReadyPostcondition() {
    return Optional.ofNullable(readyPostcondition);
  }

  public void setReadyPostcondition(Condition<R, P> readyPostcondition) {
    this.readyPostcondition = readyPostcondition;
  }

  @Override
  public List<DependentResourceNode> getParents() {
    return parents;
  }

  @Override
  public R getSecondaryResource(P primary, Context<P> context) {
    return getDependentResource().getSecondaryResource(primary, context).orElse(null);
  }
}
