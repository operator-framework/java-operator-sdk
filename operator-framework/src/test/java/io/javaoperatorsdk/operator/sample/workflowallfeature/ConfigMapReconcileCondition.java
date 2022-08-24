package io.javaoperatorsdk.operator.sample.workflowallfeature;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ConfigMapReconcileCondition
    implements Condition<ConfigMap, WorkflowAllFeatureCustomResource> {

  @Override
  public boolean isMet(WorkflowAllFeatureCustomResource primary, ConfigMap secondary,
      Context<WorkflowAllFeatureCustomResource> context) {
    return primary.getSpec().isCreateConfigMap();
  }
}
