package io.javaoperatorsdk.operator.sample.workflowactivationcondition;

import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class isOpenShiftCondition
    implements Condition<Route, WorkflowActivationConditionCustomResource> {
  @Override
  public boolean isMet(
      DependentResource<Route, WorkflowActivationConditionCustomResource> dependentResource,
      WorkflowActivationConditionCustomResource primary,
      Context<WorkflowActivationConditionCustomResource> context) {

    return context.getClient().getApiGroups().getGroups().stream()
        .anyMatch(g -> g.getName().equals("route.openshift.io"));
  }
}
