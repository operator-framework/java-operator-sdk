package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class DependentResourceNode<R, P extends HasMetadata> {

  private final DependentResource<R, P> dependentResource;
  private Condition reconcileCondition;
  private Condition deletePostCondition;
  private Condition readyCondition;
  private final List<DependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DependentResourceNode> parents = new LinkedList<>();

  public DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      Condition reconcileCondition) {
    this(dependentResource, reconcileCondition, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      Condition reconcileCondition, Condition deletePostCondition) {
    this.dependentResource = dependentResource;
    this.reconcileCondition = reconcileCondition;
    this.deletePostCondition = deletePostCondition;
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  public Optional<Condition> getReconcileCondition() {
    return Optional.ofNullable(reconcileCondition);
  }

  public Optional<Condition> getDeletePostCondition() {
    return Optional.ofNullable(deletePostCondition);
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

  public DependentResourceNode<R, P> setReconcileCondition(
      Condition reconcileCondition) {
    this.reconcileCondition = reconcileCondition;
    return this;
  }

  public DependentResourceNode<R, P> setDeletePostCondition(Condition cleanupCondition) {
    this.deletePostCondition = cleanupCondition;
    return this;
  }

  public Optional<Condition<R, P>> getReadyCondition() {
    return Optional.ofNullable(readyCondition);
  }

  public DependentResourceNode<R, P> setReadyCondition(Condition readyCondition) {
    this.readyCondition = readyCondition;
    return this;
  }

  public List<DependentResourceNode> getParents() {
    return parents;
  }
}
