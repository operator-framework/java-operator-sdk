package io.javaoperatorsdk.operator.dependent.multipledependentresource;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class MultipleDependentResourceConfigMap
    extends CRUDKubernetesDependentResource<ConfigMap, MultipleDependentResourceCustomResource> {

  public static final String DATA_KEY = "key";
  private final String value;

  public MultipleDependentResourceConfigMap(String value) {
    super(ConfigMap.class);
    this.value = value;
  }

  @Override
  protected ConfigMap desired(
      MultipleDependentResourceCustomResource primary,
      Context<MultipleDependentResourceCustomResource> context) {

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(getConfigMapName(value))
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(Map.of(DATA_KEY, primary.getSpec().getValue()))
        .build();
  }

  public static String getConfigMapName(String id) {
    return "configmap" + id;
  }
}
