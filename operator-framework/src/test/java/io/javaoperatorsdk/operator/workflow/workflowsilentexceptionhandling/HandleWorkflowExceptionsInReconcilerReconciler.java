package io.javaoperatorsdk.operator.workflow.workflowsilentexceptionhandling;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    handleExceptionsInReconciler = true,
    dependents = @Dependent(type = ConfigMapDependent.class))
@ControllerConfiguration
public class HandleWorkflowExceptionsInReconcilerReconciler
    implements Reconciler<HandleWorkflowExceptionsInReconcilerCustomResource>,
        Cleaner<HandleWorkflowExceptionsInReconcilerCustomResource> {

  private volatile boolean errorsFoundInReconcilerResult = false;
  private volatile boolean errorsFoundInCleanupResult = false;

  @Override
  public UpdateControl<HandleWorkflowExceptionsInReconcilerCustomResource> reconcile(
      HandleWorkflowExceptionsInReconcilerCustomResource resource,
      Context<HandleWorkflowExceptionsInReconcilerCustomResource> context) {

    errorsFoundInReconcilerResult =
        context
            .managedWorkflowAndDependentResourceContext()
            .getWorkflowReconcileResult()
            .orElseThrow()
            .erroredDependentsExist();

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      HandleWorkflowExceptionsInReconcilerCustomResource resource,
      Context<HandleWorkflowExceptionsInReconcilerCustomResource> context) {

    errorsFoundInCleanupResult =
        context
            .managedWorkflowAndDependentResourceContext()
            .getWorkflowCleanupResult()
            .orElseThrow()
            .erroredDependentsExist();
    return DeleteControl.defaultDelete();
  }

  public boolean isErrorsFoundInReconcilerResult() {
    return errorsFoundInReconcilerResult;
  }

  public boolean isErrorsFoundInCleanupResult() {
    return errorsFoundInCleanupResult;
  }
}
