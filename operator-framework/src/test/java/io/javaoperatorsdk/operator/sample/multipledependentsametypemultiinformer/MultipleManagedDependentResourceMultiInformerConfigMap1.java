package io.javaoperatorsdk.operator.sample.multipledependentsametypemultiinformer;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler;

@KubernetesDependent(resourceDiscriminator = ConfigMap1MultiInformerDiscriminator.class)
public class MultipleManagedDependentResourceMultiInformerConfigMap1
    extends
    CRUDKubernetesDependentResource<ConfigMap, MultipleManagedDependentResourceMultiInformerCustomResource> {

  public static final String NAME_SUFFIX = "-1";

  public MultipleManagedDependentResourceMultiInformerConfigMap1() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(MultipleManagedDependentResourceMultiInformerCustomResource primary,
      ConfigMap actual,
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
