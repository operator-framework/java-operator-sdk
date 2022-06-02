package io.javaoperatorsdk.operator.sample.workflowallfeature;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class PrivateKeySecret
    extends CRUDKubernetesDependentResource<Secret, WorkflowAllFeatureCustomResource> {

  public PrivateKeySecret(Class<ConfigMap> resourceType) {
    super(Secret.class);
  }

  @Override
  protected Secret desired(WorkflowAllFeatureCustomResource primary,
      Context<WorkflowAllFeatureCustomResource> context) {
    Secret secret = new Secret();
    secret.setMetadata(new ObjectMetaBuilder().build());


    return secret;
  }
}
