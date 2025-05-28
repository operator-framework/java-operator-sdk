package io.javaoperatorsdk.operator.workflow.workflowactivationcondition;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    dependents = {
      @Dependent(type = ConfigMapDependentResource.class),
      @Dependent(
          type = RouteDependentResource.class,
          activationCondition = IsOpenShiftCondition.class)
    })
@ControllerConfiguration
public class WorkflowActivationConditionReconciler
    implements Reconciler<WorkflowActivationConditionCustomResource> {

  @Override
  public UpdateControl<WorkflowActivationConditionCustomResource> reconcile(
      WorkflowActivationConditionCustomResource resource,
      Context<WorkflowActivationConditionCustomResource> context) {

    return UpdateControl.noUpdate();
  }
}
