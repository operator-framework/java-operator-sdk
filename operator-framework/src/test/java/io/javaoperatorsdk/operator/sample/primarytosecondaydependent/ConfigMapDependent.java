package io.javaoperatorsdk.operator.sample.primarytosecondaydependent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class ConfigMapDependent extends
    KubernetesDependentResource<ConfigMap, PrimaryToSecondaryDependentCustomResource> {

  public ConfigMapDependent() {
    super(ConfigMap.class);
  }
}
