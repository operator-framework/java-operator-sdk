package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.CleanupCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReadyCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReconcileCondition;

public class DependentBuilder<P extends HasMetadata> {

  private final WorkflowBuilder<P> workflowBuilder;
  private final DependentResourceNode<?, ?> node;

  public DependentBuilder(WorkflowBuilder<P> workflowBuilder, DependentResourceNode<?, ?> node) {
    this.workflowBuilder = workflowBuilder;
    this.node = node;
  }

  public DependentBuilder<P> dependsOn(DependentResource<?, ?>... dependentResources) {
    for (var dependentResource : dependentResources) {
      var dependsOn = workflowBuilder.getNodeByDependentResource(dependentResource);
      node.addDependsOnRelation(dependsOn);
    }
    return this;
  }

  public DependentBuilder<P> withReconcileCondition(ReconcileCondition reconcileCondition) {
    node.setReconcileCondition(reconcileCondition);
    return this;
  }

  public DependentBuilder<P> withReadyCondition(ReadyCondition readyCondition) {
    node.setReadyCondition(readyCondition);
    return this;
  }

  public DependentBuilder<P> withCleanupCondition(CleanupCondition readyCondition) {
    node.setCleanupCondition(readyCondition);
    return this;
  }

  public WorkflowBuilder<P> build() {
    return workflowBuilder;
  }

}
