package io.javaoperatorsdk.operator.dependent.multipledrsametypenodiscriminator;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class MultipleManagedDependentNoDiscriminatorConfigMap1
    extends
    CRUDKubernetesDependentResource<ConfigMap, MultipleManagedDependentNoDiscriminatorCustomResource> {

  public static final String NAME_SUFFIX = "-1";

  public MultipleManagedDependentNoDiscriminatorConfigMap1() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(MultipleManagedDependentNoDiscriminatorCustomResource primary,
      Context<MultipleManagedDependentNoDiscriminatorCustomResource> context) {
    Map<String, String> data = new HashMap<>();
    data.put(MultipleManagedDependentSameTypeNoDiscriminatorReconciler.DATA_KEY,
        primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName() + NAME_SUFFIX)
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
