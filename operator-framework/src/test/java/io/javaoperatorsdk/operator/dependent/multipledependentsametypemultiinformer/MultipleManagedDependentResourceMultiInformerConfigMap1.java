package io.javaoperatorsdk.operator.dependent.multipledependentsametypemultiinformer;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class MultipleManagedDependentResourceMultiInformerConfigMap1
    extends CRUDKubernetesDependentResource<
        ConfigMap, MultipleManagedDependentResourceMultiInformerCustomResource> {

  public static final String NAME_SUFFIX = "-1";

  @Override
  protected ConfigMap desired(
      MultipleManagedDependentResourceMultiInformerCustomResource primary,
      Context<MultipleManagedDependentResourceMultiInformerCustomResource> context) {
    Map<String, String> data = new HashMap<>();
    data.put(MultipleManagedDependentResourceReconciler.DATA_KEY, primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName() + NAME_SUFFIX)
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
