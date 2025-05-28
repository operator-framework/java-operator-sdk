package io.javaoperatorsdk.operator.workflow.workflowactivationcleanup;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    dependents = {
      @Dependent(
          type = ConfigMapDependentResource.class,
          activationCondition = TestActivcationCondition.class),
    })
@ControllerConfiguration
public class WorkflowActivationCleanupReconciler
    implements Reconciler<WorkflowActivationCleanupCustomResource>,
        Cleaner<WorkflowActivationCleanupCustomResource> {

  @Override
  public UpdateControl<WorkflowActivationCleanupCustomResource> reconcile(
      WorkflowActivationCleanupCustomResource resource,
      Context<WorkflowActivationCleanupCustomResource> context) {

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      WorkflowActivationCleanupCustomResource resource,
      Context<WorkflowActivationCleanupCustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
