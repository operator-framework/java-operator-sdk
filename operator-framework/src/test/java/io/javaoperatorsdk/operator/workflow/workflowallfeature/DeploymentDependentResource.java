package io.javaoperatorsdk.operator.workflow.workflowallfeature;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class DeploymentDependentResource
    extends CRUDNoGCKubernetesDependentResource<Deployment, WorkflowAllFeatureCustomResource> {

  @Override
  protected Deployment desired(
      WorkflowAllFeatureCustomResource primary, Context<WorkflowAllFeatureCustomResource> context) {
    Deployment deployment =
        ReconcilerUtils.loadYaml(
            Deployment.class,
            WorkflowAllFeatureIT.class,
            "/io/javaoperatorsdk/operator/nginx-deployment.yaml");
    deployment.getMetadata().setName(primary.getMetadata().getName());
    deployment.getSpec().setReplicas(2);
    deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    return deployment;
  }
}
