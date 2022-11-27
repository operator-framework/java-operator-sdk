package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

@SuppressWarnings("rawtypes")
public abstract class AbstractDependentResourceNode<R, P extends HasMetadata>
    implements DependentResourceNode<R, P> {

  private final List<DependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DependentResourceNode> parents = new LinkedList<>();
  private final String name;
  private Condition<R, P> reconcilePrecondition;
  private Condition<R, P> deletePostcondition;
  private Condition<R, P> readyPostcondition;

  protected AbstractDependentResourceNode(String name) {
    this.name = name;
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
  public List<DependentResourceNode> getParents() {
    return parents;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Optional<Condition<R, P>> getReconcilePrecondition() {
    return Optional.ofNullable(reconcilePrecondition);
  }

  @Override
  public Optional<Condition<R, P>> getDeletePostcondition() {
    return Optional.ofNullable(deletePostcondition);
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractDependentResourceNode<?, ?> that = (AbstractDependentResourceNode<?, ?>) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
