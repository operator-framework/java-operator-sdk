package io.javaoperatorsdk.operator.dependent.createonlyifnotexistsdependentwithssa;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<
        ConfigMap, CreateOnlyIfNotExistingDependentWithSSACustomResource> {

  public static final String DRKEY = "drkey";

  @Override
  protected ConfigMap desired(
      CreateOnlyIfNotExistingDependentWithSSACustomResource primary,
      Context<CreateOnlyIfNotExistingDependentWithSSACustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    configMap.setData(Map.of(DRKEY, "v"));
    return configMap;
  }
}
