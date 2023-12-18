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
  private final String name;
  private Condition<R, P> reconcilePrecondition;
  private Condition<R, P> deletePostcondition;
  private Condition<R, P> readyPostcondition;
  private Condition<R, P> activationCondition;
  private final DependentResource<R, P> dependentResource;

  DependentResourceNode(String name, DependentResource<R, P> dependentResource) {
    this(name, null, null, null, null, dependentResource);
  }

  DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(getNameFor(dependentResource), null, null, null, null, dependentResource);
  }

  public DependentResourceNode(String name, Condition<R, P> reconcilePrecondition,
      Condition<R, P> deletePostcondition, Condition<R, P> readyPostcondition,
      Condition<R, P> activationCondition, DependentResource<R, P> dependentResource) {
    this.name = name;
    this.reconcilePrecondition = reconcilePrecondition;
    this.deletePostcondition = deletePostcondition;
    this.readyPostcondition = readyPostcondition;
    this.activationCondition = activationCondition;
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

  public String getName() {
    return name;
  }


  public Optional<Condition<R, P>> getReconcilePrecondition() {
    return Optional.ofNullable(reconcilePrecondition);
  }


  public Optional<Condition<R, P>> getDeletePostcondition() {
    return Optional.ofNullable(deletePostcondition);
  }

  public Optional<Condition<R, P>> getActivationCondition() {
    return Optional.ofNullable(activationCondition);
  }

  void setReconcilePrecondition(Condition<R, P> reconcilePrecondition) {
    this.reconcilePrecondition = reconcilePrecondition;
  }

  void setDeletePostcondition(Condition<R, P> cleanupCondition) {
    this.deletePostcondition = cleanupCondition;
  }

  void setActivationCondition(Condition<R, P> activationCondition) {
    this.activationCondition = activationCondition;
  }

  public Optional<Condition<R, P>> getReadyPostcondition() {
    return Optional.ofNullable(readyPostcondition);
  }

  void setReadyPostcondition(Condition<R, P> readyPostcondition) {
    this.readyPostcondition = readyPostcondition;
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
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @SuppressWarnings("rawtypes")
  static String getNameFor(DependentResource dependentResource) {
    return DependentResource.defaultNameFor(dependentResource.getClass()) + "#"
        + dependentResource.hashCode();
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" + getDependentResource() + '}';
  }
}
