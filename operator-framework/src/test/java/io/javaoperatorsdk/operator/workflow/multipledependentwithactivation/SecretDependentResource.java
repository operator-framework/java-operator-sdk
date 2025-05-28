package io.javaoperatorsdk.operator.workflow.multipledependentwithactivation;

import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class SecretDependentResource
    extends CRUDKubernetesDependentResource<Secret, MultipleDependentActivationCustomResource> {

  @Override
  protected Secret desired(
      MultipleDependentActivationCustomResource primary,
      Context<MultipleDependentActivationCustomResource> context) {
    // basically does not matter since this should not be called
    Secret secret = new Secret();
    secret.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    secret.setData(
        Map.of(
            "data", Base64.getEncoder().encodeToString(primary.getSpec().getValue().getBytes())));
    return secret;
  }
}
