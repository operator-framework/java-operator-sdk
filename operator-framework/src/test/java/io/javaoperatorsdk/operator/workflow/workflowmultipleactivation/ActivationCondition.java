package io.javaoperatorsdk.operator.workflow.workflowmultipleactivation;

import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ActivationCondition
    implements Condition<Route, WorkflowMultipleActivationCustomResource> {

  public static volatile boolean MET = true;

  @Override
  public boolean isMet(
      DependentResource<Route, WorkflowMultipleActivationCustomResource> dependentResource,
      WorkflowMultipleActivationCustomResource primary,
      Context<WorkflowMultipleActivationCustomResource> context) {
    return MET;
  }
}
