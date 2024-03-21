package io.javaoperatorsdk.operator.sample.dependentreinitialization;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, DependentReInitializationCustomResource> {

  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(DependentReInitializationCustomResource primary,
      Context<DependentReInitializationCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build())
        .withData(Map.of("key", "val"))
        .build();
  }
}
