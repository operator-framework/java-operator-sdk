package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependsOnRelation;

public class DependentBuilder {

  private final WorkflowBuilder workflowBuilder;
  private final DependentResourceNode node;

  public DependentBuilder(WorkflowBuilder workflowBuilder, DependentResourceNode node) {
    this.workflowBuilder = workflowBuilder;
    this.node = node;
  }

  public DependentBuilder dependsOn(DependentResource<?, ?> dependentResource) {
    var dependsOn = workflowBuilder.getNodeByDependentResource(dependentResource);
    node.addDependsOnRelation(new DependsOnRelation(node, dependsOn, null));
    return this;
  }

}
