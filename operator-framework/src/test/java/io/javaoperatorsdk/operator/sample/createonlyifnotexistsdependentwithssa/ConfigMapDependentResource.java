package io.javaoperatorsdk.operator.sample.createonlyifnotexistsdependentwithssa;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ConfigMapDependentResource extends
    CRUDKubernetesDependentResource<ConfigMap, CreateOnlyIfNotExistsDependentWithSSACustomResource> {

  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(CreateOnlyIfNotExistsDependentWithSSACustomResource primary,
      Context<CreateOnlyIfNotExistsDependentWithSSACustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    configMap.setData(Map.of("drkey", "v"));
    return configMap;
  }
}


