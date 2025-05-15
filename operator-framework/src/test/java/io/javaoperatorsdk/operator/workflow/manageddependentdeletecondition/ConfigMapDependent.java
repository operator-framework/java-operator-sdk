package io.javaoperatorsdk.operator.workflow.manageddependentdeletecondition;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class ConfigMapDependent
    extends CRUDNoGCKubernetesDependentResource<
        ConfigMap, ManagedDependentDefaultDeleteConditionCustomResource> {

  @Override
  protected ConfigMap desired(
      ManagedDependentDefaultDeleteConditionCustomResource primary,
      Context<ManagedDependentDefaultDeleteConditionCustomResource> context) {

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(Map.of("key", "val"))
        .build();
  }
}
