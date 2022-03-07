package io.javaoperatorsdk.operator.sample.conditionchecker;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ConditionCheckerIT;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CrudKubernetesDependentResource;

class NginxDeploymentDependentResource extends
    CrudKubernetesDependentResource<Deployment, ConditionCheckerTestCustomResource> {

  @Override
  protected Deployment desired(ConditionCheckerTestCustomResource primary, Context context) {
    Deployment deployment =
        ReconcilerUtils.loadYaml(Deployment.class, ConditionCheckerIT.class,
            "nginx-deployment.yaml");
    deployment.getMetadata().setName(primary.getMetadata().getName());
    deployment.getSpec().setReplicas(primary.getSpec().getReplicaCount());
    deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    return deployment;
  }
}
