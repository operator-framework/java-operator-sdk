package io.javaoperatorsdk.operator.sample.workflowallfeature;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.WorkflowAllFeatureIT;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class DeploymentDependentResource extends
    CRUDNoGCKubernetesDependentResource<Deployment, WorkflowAllFeatureCustomResource> {

  public DeploymentDependentResource() {
    super(Deployment.class);
  }

  @Override
  protected Deployment desired(WorkflowAllFeatureCustomResource primary,
      Context<WorkflowAllFeatureCustomResource> context) {
    Deployment deployment =
        ReconcilerUtils.loadYaml(Deployment.class, WorkflowAllFeatureIT.class,
            "nginx-deployment.yaml");
    deployment.getMetadata().setName(primary.getMetadata().getName());
    deployment.getSpec().setReplicas(2);
    deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    return deployment;
  }
}


