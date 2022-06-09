package io.javaoperatorsdk.operator.sample.workflowallfeature;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DeploymentReadyCondition
    implements Condition<Deployment, WorkflowAllFeatureCustomResource> {
  @Override
  public boolean isMet(
      DependentResource<Deployment, WorkflowAllFeatureCustomResource> dependentResource,
      WorkflowAllFeatureCustomResource primary, Context<WorkflowAllFeatureCustomResource> context) {

    var deployment = dependentResource.getSecondaryResource(primary).orElseThrow();
    var readyReplicas = deployment.getStatus().getReadyReplicas();

    return readyReplicas != null && deployment.getSpec().getReplicas().equals(readyReplicas);
  }
}
