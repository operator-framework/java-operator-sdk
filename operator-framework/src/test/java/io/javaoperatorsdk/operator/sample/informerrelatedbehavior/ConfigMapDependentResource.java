package io.javaoperatorsdk.operator.sample.informerrelatedbehavior;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(labelSelector = "app=rbac-test")
public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, InformerRelatedBehaviorTestCustomResource> {

  public static final String DATA_KEY = "key";

  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(InformerRelatedBehaviorTestCustomResource primary,
      Context<InformerRelatedBehaviorTestCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withLabels(Map.of("app", "rbac-test"))
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build())
        .withData(Map.of(DATA_KEY, primary.getMetadata().getName()))
        .build();

  }
}
