package io.javaoperatorsdk.operator.sample.workflowactivationcleanup;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(dependents = {
    @Dependent(type = ConfigMapDependentResource.class,
        activationCondition = TestActivcationCondition.class),
})
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
  public DeleteControl cleanup(WorkflowActivationCleanupCustomResource resource,
      Context<WorkflowActivationCleanupCustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
