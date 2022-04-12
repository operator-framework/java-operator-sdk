package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.WaitCondition;

public class DependsOnRelation {

  private DependentResourceNode<?, ?> owner;
  private DependentResourceNode<?, ?> dependsOn;
  private WaitCondition waitCondition;

  public DependsOnRelation() {}

  public DependsOnRelation(DependentResourceNode<?, ?> owner,
      DependentResourceNode<?, ?> dependsOn) {
    this(owner, dependsOn, null);
  }

  public DependsOnRelation(DependentResourceNode<?, ?> owner, DependentResourceNode<?, ?> dependsOn,
      WaitCondition waitCondition) {
    this.owner = owner;
    this.dependsOn = dependsOn;
    this.waitCondition = waitCondition;
  }

  public DependentResourceNode<?, ?> getOwner() {
    return owner;
  }

  public DependentResourceNode<?, ?> getDependsOn() {
    return dependsOn;
  }

  public WaitCondition getWaitCondition() {
    return waitCondition;
  }
}
