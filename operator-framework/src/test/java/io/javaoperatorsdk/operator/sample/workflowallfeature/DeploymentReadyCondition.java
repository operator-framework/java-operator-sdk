package io.javaoperatorsdk.operator.sample.workflowallfeature;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DeploymentReadyCondition
    implements Condition<Deployment, WorkflowAllFeatureCustomResource> {
  @Override
  public boolean isMet(WorkflowAllFeatureCustomResource primary, Optional<Deployment> secondary,
      Context<WorkflowAllFeatureCustomResource> context) {
    var deployment = secondary.orElseThrow();
    var readyReplicas = deployment.getStatus().getReadyReplicas();

    return readyReplicas != null && deployment.getSpec().getReplicas().equals(readyReplicas);
  }
}
