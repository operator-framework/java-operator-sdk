package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;

import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

public interface ManagedDependentResourceContext {
  <T> Optional<T> get(Object key, Class<T> expectedType);

  @SuppressWarnings("unchecked")
  <T> T put(Object key, T value);

  @SuppressWarnings("unused")
  <T> T getMandatory(Object key, Class<T> expectedType);

  Optional<WorkflowReconcileResult> getWorkflowReconcileResult();

  Optional<WorkflowCleanupResult> getWorkflowCleanupResult();
}
