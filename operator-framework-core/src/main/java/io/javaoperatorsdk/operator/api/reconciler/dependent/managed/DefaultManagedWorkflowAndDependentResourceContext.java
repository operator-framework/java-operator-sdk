package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

@SuppressWarnings("rawtypes")
public class DefaultManagedWorkflowAndDependentResourceContext<P extends HasMetadata>
    implements ManagedWorkflowAndDependentResourceContext {

  public static final Object RECONCILE_RESULT_KEY = new Object();
  public static final Object CLEANUP_RESULT_KEY = new Object();
  private final ConcurrentHashMap attributes = new ConcurrentHashMap();
  private final Controller<P> controller;
  private final P primaryResource;
  private final Context<P> context;

  public DefaultManagedWorkflowAndDependentResourceContext(Controller<P> controller,
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

  @Override
  public WorkflowReconcileResult getWorkflowReconcileResult() {
    return getMandatory(RECONCILE_RESULT_KEY, WorkflowReconcileResult.class);
  }

  @Override
  public WorkflowCleanupResult getWorkflowCleanupResult() {
    return getMandatory(CLEANUP_RESULT_KEY, WorkflowCleanupResult.class);
  }

  @Override
  public WorkflowReconcileResult reconcileManagedWorkflow() {
    if (!controller.isWorkflowExplicitInvocation()) {
      throw new IllegalStateException("Workflow explicit invocation is not set.");
    }
    return controller.reconcileManagedWorkflow(primaryResource, context);
  }

  @Override
  public WorkflowCleanupResult cleanupManageWorkflow() {
    if (!controller.isWorkflowExplicitInvocation()) {
      throw new IllegalStateException("Workflow explicit invocation is not set.");
    }
    return controller.cleanupManagedWorkflow(primaryResource, context);
  }
}
