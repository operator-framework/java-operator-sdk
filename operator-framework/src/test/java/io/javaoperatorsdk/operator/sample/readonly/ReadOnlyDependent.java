package io.javaoperatorsdk.operator.sample.readonly;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class ReadOnlyDependent extends KubernetesDependentResource<ConfigMap, ConfigMapReader> {

  public ReadOnlyDependent() {
    super(ConfigMap.class);
  }
}
