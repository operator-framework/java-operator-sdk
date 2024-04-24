package io.javaoperatorsdk.operator.sample.workflowsilentexceptionhandling;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(silentExceptionHandling = true,
    dependents = @Dependent(type = ConfigMapDependent.class))
@ControllerConfiguration
public class WorkflowSilentExceptionHandlingReconciler
    implements Reconciler<WorkflowSilentExceptionHandlingCustomResource>,
    Cleaner<WorkflowSilentExceptionHandlingCustomResource> {

  private volatile boolean errorsFoundInReconcilerResult = false;
  private volatile boolean errorsFoundInCleanupResult = false;

  @Override
  public UpdateControl<WorkflowSilentExceptionHandlingCustomResource> reconcile(
      WorkflowSilentExceptionHandlingCustomResource resource,
      Context<WorkflowSilentExceptionHandlingCustomResource> context) {

    errorsFoundInReconcilerResult = context.managedWorkflowAndDependentResourceContext()
        .getWorkflowReconcileResult().erroredDependentsExist();


    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(WorkflowSilentExceptionHandlingCustomResource resource,
      Context<WorkflowSilentExceptionHandlingCustomResource> context) {

    errorsFoundInCleanupResult = context.managedWorkflowAndDependentResourceContext()
        .getWorkflowCleanupResult().erroredDependentsExist();
    return DeleteControl.defaultDelete();
  }

  public boolean isErrorsFoundInReconcilerResult() {
    return errorsFoundInReconcilerResult;
  }

  public boolean isErrorsFoundInCleanupResult() {
    return errorsFoundInCleanupResult;
  }
}
