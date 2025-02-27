package io.javaoperatorsdk.operator.workflow.workflowexplicitcleanup;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(explicitInvocation = true, dependents = @Dependent(type = ConfigMapDependent.class))
@ControllerConfiguration
public class WorkflowExplicitCleanupReconciler
    implements Reconciler<WorkflowExplicitCleanupCustomResource>,
        Cleaner<WorkflowExplicitCleanupCustomResource> {

  @Override
  public UpdateControl<WorkflowExplicitCleanupCustomResource> reconcile(
      WorkflowExplicitCleanupCustomResource resource,
      Context<WorkflowExplicitCleanupCustomResource> context) {

    context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow();

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      WorkflowExplicitCleanupCustomResource resource,
      Context<WorkflowExplicitCleanupCustomResource> context) {

    context.managedWorkflowAndDependentResourceContext().cleanupManageWorkflow();
    // this can be checked
    // context.managedWorkflowAndDependentResourceContext().getWorkflowCleanupResult()
    return DeleteControl.defaultDelete();
  }
}
