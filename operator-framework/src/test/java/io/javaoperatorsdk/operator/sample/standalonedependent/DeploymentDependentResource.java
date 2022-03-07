package io.javaoperatorsdk.operator.sample.standalonedependent;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.StandaloneDependentResourceIT;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Creator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

class DeploymentDependentResource extends
    KubernetesDependentResource<Deployment, StandaloneDependentTestCustomResource>
    implements Creator<Deployment, StandaloneDependentTestCustomResource>,
    Updater<Deployment, StandaloneDependentTestCustomResource> {

  @Override
  protected Deployment desired(StandaloneDependentTestCustomResource primary, Context context) {
    Deployment deployment =
        ReconcilerUtils.loadYaml(Deployment.class, StandaloneDependentResourceIT.class,
            "nginx-deployment.yaml");
    deployment.getMetadata().setName(primary.getMetadata().getName());
    deployment.getSpec().setReplicas(primary.getSpec().getReplicaCount());
    deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    return deployment;
  }
}
