package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.WaitCondition;

public class DependsOnRelation {

    private DependentResourceNode owner;
    private DependentResourceNode dependsOn;
    private WaitCondition waitCondition;

    public DependsOnRelation() {
    }

    public DependsOnRelation(DependentResourceNode owner, DependentResourceNode dependsOn, WaitCondition waitCondition) {
        this.owner = owner;
        this.dependsOn = dependsOn;
        this.waitCondition = waitCondition;
    }


    public void setOwner(DependentResourceNode owner) {
        this.owner = owner;
    }

    public void setDependsOn(DependentResourceNode dependsOn) {
        this.dependsOn = dependsOn;
    }

    public void setWaitCondition(WaitCondition waitCondition) {
        this.waitCondition = waitCondition;
    }

    public DependentResourceNode getOwner() {
        return owner;
    }

    public DependentResourceNode getDependsOn() {
        return dependsOn;
    }

    public WaitCondition getWaitCondition() {
        return waitCondition;
    }
}
