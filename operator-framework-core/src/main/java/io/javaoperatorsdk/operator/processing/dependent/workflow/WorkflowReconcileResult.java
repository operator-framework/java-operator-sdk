package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public interface WorkflowReconcileResult extends WorkflowResult {
  WorkflowReconcileResult EMPTY = new WorkflowReconcileResult() {};

  default List<DependentResource> getReconciledDependents() {
    return List.of();
  }

  default List<DependentResource> getNotReadyDependents() {
    return List.of();
  }

  default <T> Optional<T> getNotReadyDependentResult(
      DependentResource dependentResource, Class<T> expectedResultType) {
    return Optional.empty();
  }

  default boolean allDependentResourcesReady() {
    return getNotReadyDependents().isEmpty();
  }
}
