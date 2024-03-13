package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

@SuppressWarnings("rawtypes")
public class DefaultManagedDependentResourceContext<P extends HasMetadata>
    implements ManagedDependentResourceContext {

  private final ConcurrentHashMap attributes = new ConcurrentHashMap();
  private final Controller<P> controller;
  private final P primaryResource;
  private final Context<P> context;
  private WorkflowReconcileResult workflowReconcileResult;
  private WorkflowCleanupResult workflowCleanupResult;

  public DefaultManagedDependentResourceContext(Controller<P> controller,
      P primaryResource,
      Context<P> context) {
    this.controller = controller;
    this.primaryResource = primaryResource;
    this.context = context;
  }

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
  public WorkflowReconcileResult getWorkflowReconcileResult() {
    return workflowReconcileResult;
  }

  @Override
  public WorkflowCleanupResult getWorkflowCleanupResult() {
    return workflowCleanupResult;
  }

  @Override
  public void invokeWorkflow() {
    controller.invokeManagedWorkflow(primaryResource, context);
  }

}
