package io.javaoperatorsdk.operator.sample.multipleupdateondependent;

import java.util.HashMap;
import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.BooleanWithUndefined;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(useSSA = BooleanWithUndefined.TRUE)
public class MultipleOwnerDependentConfigMap
    extends
    CRUDKubernetesDependentResource<ConfigMap, MultipleOwnerDependentCustomResource> {

  public static final String RESOURCE_NAME = "test1";

  public MultipleOwnerDependentConfigMap() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(MultipleOwnerDependentCustomResource primary,
      Context<MultipleOwnerDependentCustomResource> context) {

    var cm = getSecondaryResource(primary, context);

    var data = cm.map(ConfigMap::getData).orElse(new HashMap<>());
    data.put(primary.getSpec().getValue(), primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(RESOURCE_NAME)
        .withNamespace(primary.getMetadata().getNamespace())
        .withOwnerReferences(cm.map(c -> c.getMetadata().getOwnerReferences()).orElse(List.of()))
        .endMetadata()
        .withData(data)
        .build();
  }
}
