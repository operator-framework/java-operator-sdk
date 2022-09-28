package io.javaoperatorsdk.operator.sample.indexdiscriminator;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class IndexDiscriminatorTestDRConfigMap
    extends CRUDNoGCKubernetesDependentResource<ConfigMap, IndexDiscriminatorTestCustomResource> {

  public static final String DATA_KEY = "key";
  private final String suffix;

  public IndexDiscriminatorTestDRConfigMap(String value) {
    super(ConfigMap.class);
    this.suffix = value;
  }

  @Override
  protected ConfigMap desired(IndexDiscriminatorTestCustomResource primary,
      Context<IndexDiscriminatorTestCustomResource> context) {
    Map<String, String> data = new HashMap<>();
    data.put(DATA_KEY, primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName() + suffix)
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
