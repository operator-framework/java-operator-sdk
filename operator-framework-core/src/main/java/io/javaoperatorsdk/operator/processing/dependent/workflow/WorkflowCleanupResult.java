package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public interface WorkflowCleanupResult extends WorkflowResult {
  WorkflowCleanupResult EMPTY = new WorkflowCleanupResult() {};

  default List<DependentResource> getDeleteCalledOnDependents() {
    return List.of();
  }

  default List<DependentResource> getPostConditionNotMetDependents() {
    return List.of();
  }

  default boolean allPostConditionsMet() {
    return true;
  }
}
