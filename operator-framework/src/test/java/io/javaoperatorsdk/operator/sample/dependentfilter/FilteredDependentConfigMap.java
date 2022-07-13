package io.javaoperatorsdk.operator.sample.dependentfilter;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.sample.dependentfilter.DependentFilterTestReconciler.CM_VALUE_KEY;

@KubernetesDependent(onUpdateFilter = UpdateFilter.class)
public class FilteredDependentConfigMap
    extends CRUDKubernetesDependentResource<ConfigMap, DependentFilterTestCustomResource> {

  public FilteredDependentConfigMap() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(DependentFilterTestCustomResource primary,
      Context<DependentFilterTestCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    configMap.setData(Map.of(CM_VALUE_KEY, primary.getSpec().getValue()));
    return configMap;
  }
}
