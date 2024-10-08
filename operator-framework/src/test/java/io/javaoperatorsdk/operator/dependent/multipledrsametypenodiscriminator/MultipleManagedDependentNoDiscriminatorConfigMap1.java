package io.javaoperatorsdk.operator.dependent.multipledrsametypenodiscriminator;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@KubernetesDependent
public class MultipleManagedDependentNoDiscriminatorConfigMap1
    extends
    CRUDKubernetesDependentResource<ConfigMap, MultipleManagedDependentNoDiscriminatorCustomResource> {

  public static final String NAME_SUFFIX = "-1";

  public MultipleManagedDependentNoDiscriminatorConfigMap1() {
    super(ConfigMap.class);
  }

  // This is to showcase optimization to not compute the whole desired state only the target
  // resource ID. In this particular case this would not be necessary, since desired state creation
  // is pretty light weigh. Use it in more heavyweight situations.
  protected ResourceID targetSecondaryResourceID(
      MultipleManagedDependentNoDiscriminatorCustomResource primary,
      Context<MultipleManagedDependentNoDiscriminatorCustomResource> context) {
    return new ResourceID(primary.getMetadata().getName() + NAME_SUFFIX,
        primary.getMetadata().getNamespace());
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
