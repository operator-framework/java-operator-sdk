package io.javaoperatorsdk.operator.dependent.multipledependentresourcewithsametype;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class MultipleDependentResourceConfigMap
    extends CRUDKubernetesDependentResource<
        ConfigMap, MultipleDependentResourceCustomResourceNoDiscriminator> {

  public static final String DATA_KEY = "key";
  private final int value;

  public MultipleDependentResourceConfigMap(int value) {
    super(ConfigMap.class);
    this.value = value;
  }

  @Override
  protected ConfigMap desired(
      MultipleDependentResourceCustomResourceNoDiscriminator primary,
      Context<MultipleDependentResourceCustomResourceNoDiscriminator> context) {
    Map<String, String> data = new HashMap<>();
    data.put(DATA_KEY, String.valueOf(value));

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getConfigMapName(value))
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
