package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;

@SuppressWarnings("rawtypes")
public class DependentBuilder<P extends HasMetadata> {

  private final WorkflowBuilder<P> workflowBuilder;
  private final DependentResourceNode<?, ?> node;

  public DependentBuilder(WorkflowBuilder<P> workflowBuilder, DependentResourceNode<?, ?> node) {
    this.workflowBuilder = workflowBuilder;
    this.node = node;
  }

  public DependentBuilder<P> dependsOn(Set<DependentResource> dependentResources) {
    for (var dependentResource : dependentResources) {
      var dependsOn = workflowBuilder.getNodeByDependentResource(dependentResource);
      node.addDependsOnRelation(dependsOn);
    }
    return this;
  }

  public DependentBuilder<P> dependsOn(DependentResource... dependentResources) {
    return dependsOn(new HashSet<>(Arrays.asList(dependentResources)));
  }

  public DependentBuilder<P> withReconcilePrecondition(Condition reconcilePrecondition) {
    node.setReconcilePrecondition(reconcilePrecondition);
    return this;
  }

  public DependentBuilder<P> withReadyPostcondition(Condition readyPostcondition) {
    node.setReadyPostcondition(readyPostcondition);
    return this;
  }

  public DependentBuilder<P> withDeletePostcondition(Condition deletePostcondition) {
    node.setDeletePostcondition(deletePostcondition);
    return this;
  }

  public WorkflowBuilder<P> build() {
    return workflowBuilder;
  }

}
