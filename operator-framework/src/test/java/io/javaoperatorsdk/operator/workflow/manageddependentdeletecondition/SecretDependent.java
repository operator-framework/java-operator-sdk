package io.javaoperatorsdk.operator.workflow.manageddependentdeletecondition;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class SecretDependent
    extends CRUDNoGCKubernetesDependentResource<
        Secret, ManagedDependentDefaultDeleteConditionCustomResource> {

  @Override
  protected Secret desired(
      ManagedDependentDefaultDeleteConditionCustomResource primary,
      Context<ManagedDependentDefaultDeleteConditionCustomResource> context) {

    return new SecretBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(
            Map.of(
                "key",
                new String(Base64.getEncoder().encode("val".getBytes(StandardCharsets.UTF_16)))))
        .build();
  }
}
