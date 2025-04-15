package io.javaoperatorsdk.operator.workflow.getnonactivesecondary;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, GetNonActiveSecondaryCustomResource> {

  public static final String DATA_KEY = "data";

  @Override
  protected ConfigMap desired(
      GetNonActiveSecondaryCustomResource primary,
      Context<GetNonActiveSecondaryCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    return configMap;
  }
}
