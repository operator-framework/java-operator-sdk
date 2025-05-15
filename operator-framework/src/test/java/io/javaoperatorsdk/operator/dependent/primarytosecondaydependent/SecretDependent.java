package io.javaoperatorsdk.operator.dependent.primarytosecondaydependent;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

import static io.javaoperatorsdk.operator.dependent.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler.DATA_KEY;

public class SecretDependent
    extends CRUDKubernetesDependentResource<Secret, PrimaryToSecondaryDependentCustomResource> {

  @Override
  protected Secret desired(
      PrimaryToSecondaryDependentCustomResource primary,
      Context<PrimaryToSecondaryDependentCustomResource> context) {
    Secret secret = new Secret();
    secret.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    secret.setData(
        Map.of(
            DATA_KEY,
            context.getSecondaryResource(ConfigMap.class).orElseThrow().getData().get(DATA_KEY)));
    return secret;
  }
}
