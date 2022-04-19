package io.javaoperatorsdk.operator.sample.dependentoperationeventfiltering;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class ConfigMapDependentResource extends
    io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource<ConfigMap, DependentOperationEventFilterCustomResource>
    implements
    io.javaoperatorsdk.operator.processing.dependent.Creator<ConfigMap, DependentOperationEventFilterCustomResource>,
    io.javaoperatorsdk.operator.processing.dependent.Updater<ConfigMap, DependentOperationEventFilterCustomResource> {

  public static final String KEY = "key1";

  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(DependentOperationEventFilterCustomResource primary,
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
