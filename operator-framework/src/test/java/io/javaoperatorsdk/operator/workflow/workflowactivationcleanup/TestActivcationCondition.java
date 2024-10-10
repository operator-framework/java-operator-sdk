package io.javaoperatorsdk.operator.workflow.workflowactivationcleanup;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class TestActivcationCondition
    implements Condition<ConfigMap, WorkflowActivationCleanupCustomResource> {

  @Override
  public boolean isMet(
      DependentResource<ConfigMap, WorkflowActivationCleanupCustomResource> dependentResource,
      WorkflowActivationCleanupCustomResource primary,
      Context<WorkflowActivationCleanupCustomResource> context) {
    return true;
  }
}
