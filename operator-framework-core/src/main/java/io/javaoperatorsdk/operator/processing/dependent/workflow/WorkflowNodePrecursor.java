package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

abstract class WorkflowNodePrecursor<R, P extends HasMetadata> extends DependentResourceNode<R, P> {
  protected WorkflowNodePrecursor(DependentResourceNode<R, P> other) {
    super(other);
  }

  protected WorkflowNodePrecursor(Condition<R, P> reconcilePrecondition,
      Condition<R, P> deletePostcondition, Condition<R, P> readyPostcondition,
      Condition<R, P> activationCondition) {
    super(reconcilePrecondition, deletePostcondition, readyPostcondition, activationCondition);
  }

  abstract Set<String> dependsOnAsNames();
}
