package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class DependentResourceNode<R, P extends HasMetadata> {

  private final DependentResource<R, P> dependentResource;
  private Condition reconcilePrecondition;
  private Condition deletePostcondition;
  private Condition readyPostcondition;
  private final List<DependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DependentResourceNode> parents = new LinkedList<>();

  public DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      Condition reconcilePrecondition) {
    this(dependentResource, reconcilePrecondition, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      Condition reconcilePrecondition, Condition deletePostcondition) {
    this.dependentResource = dependentResource;
    this.reconcilePrecondition = reconcilePrecondition;
    this.deletePostcondition = deletePostcondition;
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  public Optional<Condition> getReconcilePrecondition() {
    return Optional.ofNullable(reconcilePrecondition);
  }

  public Optional<Condition> getDeletePostcondition() {
    return Optional.ofNullable(deletePostcondition);
  }

  public List<DependentResourceNode> getDependsOn() {
    return dependsOn;
  }

  @SuppressWarnings("unchecked")
  public void addDependsOnRelation(DependentResourceNode node) {
    node.parents.add(this);
    dependsOn.add(node);
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" +
        "dependentResource=" + dependentResource +
        '}';
  }

  public DependentResourceNode<R, P> setReconcilePrecondition(
      Condition reconcilePrecondition) {
    this.reconcilePrecondition = reconcilePrecondition;
    return this;
  }

  public DependentResourceNode<R, P> setDeletePostcondition(Condition cleanupCondition) {
    this.deletePostcondition = cleanupCondition;
    return this;
  }

  public Optional<Condition<R, P>> getReadyPostcondition() {
    return Optional.ofNullable(readyPostcondition);
  }

  public DependentResourceNode<R, P> setReadyPostcondition(Condition readyPostcondition) {
    this.readyPostcondition = readyPostcondition;
    return this;
  }

  public List<DependentResourceNode> getParents() {
    return parents;
  }
}
