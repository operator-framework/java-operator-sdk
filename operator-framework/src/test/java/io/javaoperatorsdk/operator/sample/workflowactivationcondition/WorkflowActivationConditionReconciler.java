package io.javaoperatorsdk.operator.sample.workflowactivationcondition;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(dependents = {
    @Dependent(type = ConfigMapDependentResource.class),
    @Dependent(type = RouteDependentResource.class,
        activationCondition = isOpenShiftCondition.class)
})
public class WorkflowActivationConditionReconciler
    implements Reconciler<WorkflowActivationConditionCustomResource> {

  @Override
  public UpdateControl<WorkflowActivationConditionCustomResource> reconcile(
      WorkflowActivationConditionCustomResource resource,
      Context<WorkflowActivationConditionCustomResource> context) {

    return UpdateControl.noUpdate();
  }
}
