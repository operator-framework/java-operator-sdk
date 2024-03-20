package io.javaoperatorsdk.operator.sample.multipledrsametypenodiscriminator;

import static io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler.DATA_KEY;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.HashMap;
import java.util.Map;

@KubernetesDependent
public class MultipleManagedDependentNoDiscriminatorConfigMap2
    extends
    CRUDKubernetesDependentResource<ConfigMap, MultipleManagedDependentNoDiscriminatorCustomResource> {

  public static final String NAME_SUFFIX = "-2";

  public MultipleManagedDependentNoDiscriminatorConfigMap2() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(MultipleManagedDependentNoDiscriminatorCustomResource primary,
      Context<MultipleManagedDependentNoDiscriminatorCustomResource> context) {
    Map<String, String> data = new HashMap<>();
    data.put(DATA_KEY, primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName() + NAME_SUFFIX)
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
