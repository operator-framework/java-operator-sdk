package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReadyCondition;

public class DependsOnRelation {

  private DependentResourceNode<?, ?> owner;
  private DependentResourceNode<?, ?> dependsOn;
  private ReadyCondition readyCondition;

  public DependsOnRelation() {}

  public DependsOnRelation(DependentResourceNode<?, ?> owner,
      DependentResourceNode<?, ?> dependsOn) {
    this(owner, dependsOn, null);
  }

  public DependsOnRelation(DependentResourceNode<?, ?> owner, DependentResourceNode<?, ?> dependsOn,
      ReadyCondition readyCondition) {
    this.owner = owner;
    this.dependsOn = dependsOn;
    this.readyCondition = readyCondition;
  }

  public DependentResourceNode<?, ?> getOwner() {
    return owner;
  }

  public DependentResourceNode<?, ?> getDependsOn() {
    return dependsOn;
  }

  public ReadyCondition getWaitCondition() {
    return readyCondition;
  }
}
