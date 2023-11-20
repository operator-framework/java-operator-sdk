package io.javaoperatorsdk.operator.sample.workflowactivationcondition;

import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class IsOpenShiftCondition
    implements Condition<Route, WorkflowActivationConditionCustomResource> {
  @Override
  public boolean isMet(
      DependentResource<Route, WorkflowActivationConditionCustomResource> dependentResource,
      WorkflowActivationConditionCustomResource primary,
      Context<WorkflowActivationConditionCustomResource> context) {
    // we are testing if the reconciliation still works on Kubernetes, so this always false;
    return false;
  }
}
