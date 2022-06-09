package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

@SuppressWarnings("rawtypes")
public class DefaultManagedDependentResourceContext implements ManagedDependentResourceContext {

  private WorkflowReconcileResult workflowReconcileResult;
  private WorkflowCleanupResult workflowCleanupResult;
  private final ConcurrentHashMap attributes = new ConcurrentHashMap();

  @Override
  public <T> Optional<T> get(Object key, Class<T> expectedType) {
    return Optional.ofNullable(attributes.get(key))
        .filter(expectedType::isInstance)
        .map(expectedType::cast);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T put(Object key, T value) {
    if (value == null) {
      return (T) Optional.ofNullable(attributes.remove(key));
    }
    return (T) Optional.ofNullable(attributes.put(key, value));
  }

  @Override
  @SuppressWarnings("unused")
  public <T> T getMandatory(Object key, Class<T> expectedType) {
    return get(key, expectedType).orElseThrow(() -> new IllegalStateException(
        "Mandatory attribute (key: " + key + ", type: " + expectedType.getName()
            + ") is missing or not of the expected type"));
  }

  public DefaultManagedDependentResourceContext setWorkflowExecutionResult(
      WorkflowReconcileResult workflowReconcileResult) {
    this.workflowReconcileResult = workflowReconcileResult;
    return this;
  }

  public DefaultManagedDependentResourceContext setWorkflowCleanupResult(
      WorkflowCleanupResult workflowCleanupResult) {
    this.workflowCleanupResult = workflowCleanupResult;
    return this;
  }

  @Override
  public Optional<WorkflowReconcileResult> getWorkflowReconcileResult() {
    return Optional.ofNullable(workflowReconcileResult);
  }

  @Override
  public Optional<WorkflowCleanupResult> getWorkflowCleanupResult() {
    return Optional.ofNullable(workflowCleanupResult);
  }
}
