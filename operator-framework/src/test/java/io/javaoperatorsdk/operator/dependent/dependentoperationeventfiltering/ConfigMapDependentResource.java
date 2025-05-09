package io.javaoperatorsdk.operator.dependent.dependentoperationeventfiltering;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<
        ConfigMap, DependentOperationEventFilterCustomResource> {

  public static final String KEY = "key1";

  @Override
  protected ConfigMap desired(
      DependentOperationEventFilterCustomResource primary,
      Context<DependentOperationEventFilterCustomResource> context) {

    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(primary.getMetadata().getName());
    configMap.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    HashMap<String, String> data = new HashMap<>();
    data.put(KEY, primary.getSpec().getValue());
    configMap.setData(data);
    return configMap;
  }
}
