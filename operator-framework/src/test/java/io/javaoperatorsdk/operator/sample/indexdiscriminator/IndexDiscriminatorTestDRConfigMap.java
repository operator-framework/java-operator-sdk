package io.javaoperatorsdk.operator.sample.indexdiscriminator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@KubernetesDependent
public class IndexDiscriminatorTestDRConfigMap
    extends CRUDNoGCKubernetesDependentResource<ConfigMap, IndexDiscriminatorTestCustomResource> {

  public static final String DATA_KEY = "key";
  private final String suffix;
  private final String indexName;

  public IndexDiscriminatorTestDRConfigMap(String suffix, String indexName) {
    super(ConfigMap.class);
    this.suffix = suffix;
    this.indexName = indexName;
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

  @Override
  public void configureWith(
      InformerEventSource<ConfigMap, IndexDiscriminatorTestCustomResource> eventSource) {
    eventSource.addIndexer(indexName, cm -> {
      if (cm.getMetadata().getName().endsWith(suffix)) {
        return List.of(configMapKey(cm));
      } else {
        return Collections.emptyList();
      }
    });
    super.configureWith(eventSource);
  }

  @Override
  public Optional<ConfigMap> getSecondaryResource(IndexDiscriminatorTestCustomResource primary) {
    var resources = eventSource().byIndex(indexName, configMapKeyFromPrimary(primary, suffix));
    if (resources.isEmpty()) {
      return Optional.empty();
    } else if (resources.size() > 1) {
      throw new IllegalStateException("more than one resource");
    } else {
      return Optional.of(resources.get(0));
    }
  }

  private String configMapKey(ConfigMap configMap) {
    return configMap.getMetadata().getName() + "#" + configMap.getMetadata().getNamespace();
  }

  private String configMapKeyFromPrimary(IndexDiscriminatorTestCustomResource primary,
      String nameSuffix) {
    return primary.getMetadata().getName() + nameSuffix + "#"
        + primary.getMetadata().getNamespace();
  }

}
