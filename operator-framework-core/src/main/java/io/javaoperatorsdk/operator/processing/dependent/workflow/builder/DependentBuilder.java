package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependsOnRelation;

public class DependentBuilder<P extends HasMetadata> {

  private final WorkflowBuilder<P> workflowBuilder;
  private final DependentResourceNode node;

  public DependentBuilder(WorkflowBuilder<P> workflowBuilder, DependentResourceNode node) {
    this.workflowBuilder = workflowBuilder;
    this.node = node;
  }

  public DependentBuilder<P> dependsOn(DependentResource dependentResource) {
    var dependsOn = workflowBuilder.getNodeByDependentResource(dependentResource);
    node.addDependsOnRelation(new DependsOnRelation(node, dependsOn, null));
    return this;
  }

  public WorkflowBuilder<P> build() {
    return workflowBuilder;
  }

}
