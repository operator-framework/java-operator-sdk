package io.javaoperatorsdk.operator.dependent.dependentdifferentnamespace;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class ConfigMapDependentResource
    extends CRUDNoGCKubernetesDependentResource<
        ConfigMap, DependentDifferentNamespaceCustomResource> {

  public static final String KEY = "key";

  public static final String NAMESPACE = "default";

  @Override
  protected ConfigMap desired(
      DependentDifferentNamespaceCustomResource primary,
      Context<DependentDifferentNamespaceCustomResource> context) {

    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(primary.getMetadata().getName());
    configMap.getMetadata().setNamespace(NAMESPACE);
    HashMap<String, String> data = new HashMap<>();
    data.put(KEY, primary.getSpec().getValue());
    configMap.setData(data);
    return configMap;
  }
}
