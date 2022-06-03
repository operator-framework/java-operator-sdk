package io.javaoperatorsdk.operator.sample.workflowallfeature;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ConfigMapDeletePostCondition
    implements Condition<ConfigMap, WorkflowAllFeatureCustomResource> {

  @Override
  public boolean isMet(
      DependentResource<ConfigMap, WorkflowAllFeatureCustomResource> dependentResource,
      WorkflowAllFeatureCustomResource primary, Context<WorkflowAllFeatureCustomResource> context) {
    return dependentResource.getSecondaryResource(primary).isPresent();
  }
}
