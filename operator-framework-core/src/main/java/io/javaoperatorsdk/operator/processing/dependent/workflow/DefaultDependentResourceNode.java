package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class DefaultDependentResourceNode<R, P extends HasMetadata> {

  private final DependentResource<R, P> dependentResource;
  private Condition<R, P> reconcilePrecondition;
  private Condition<R, P> deletePostcondition;
  private Condition<R, P> readyPostcondition;
  private final List<DefaultDependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DefaultDependentResourceNode> parents = new LinkedList<>();

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

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  public Optional<Condition<R, P>> getReconcilePrecondition() {
    return Optional.ofNullable(reconcilePrecondition);
  }

  public Optional<Condition<R, P>> getDeletePostcondition() {
    return Optional.ofNullable(deletePostcondition);
  }

  public List<DefaultDependentResourceNode> getDependsOn() {
    return dependsOn;
  }

  @SuppressWarnings("unchecked")
  public void addDependsOnRelation(DefaultDependentResourceNode node) {
    node.parents.add(this);
    dependsOn.add(node);
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" +
        "dependentResource=" + dependentResource +
        '}';
  }

  public DefaultDependentResourceNode<R, P> setReconcilePrecondition(
      Condition<R, P> reconcilePrecondition) {
    this.reconcilePrecondition = reconcilePrecondition;
    return this;
  }

  public DefaultDependentResourceNode<R, P> setDeletePostcondition(
      Condition<R, P> cleanupCondition) {
    this.deletePostcondition = cleanupCondition;
    return this;
  }

  public Optional<Condition<R, P>> getReadyPostcondition() {
    return Optional.ofNullable(readyPostcondition);
  }

  public DefaultDependentResourceNode<R, P> setReadyPostcondition(
      Condition<R, P> readyPostcondition) {
    this.readyPostcondition = readyPostcondition;
    return this;
  }

  public List<DefaultDependentResourceNode> getParents() {
    return parents;
  }

  protected R getSecondaryResource(P primary, Context<P> context) {
    return getDependentResource().getSecondaryResource(primary, context).orElse(null);
  }
}
