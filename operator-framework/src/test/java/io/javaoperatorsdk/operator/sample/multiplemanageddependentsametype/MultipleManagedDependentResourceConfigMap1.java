package io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler.CONFIG_MAP_EVENT_SOURCE;

@KubernetesDependent(eventSourceToUse = CONFIG_MAP_EVENT_SOURCE,
    resourceDiscriminator = ConfigMap1Discriminator.class)
public class MultipleManagedDependentResourceConfigMap1
    extends
    CRUDKubernetesDependentResource<ConfigMap, MultipleManagedDependentResourceCustomResource> {

  public static final String NAME_SUFFIX = "-1";

  public MultipleManagedDependentResourceConfigMap1() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(MultipleManagedDependentResourceCustomResource primary,
      Context<MultipleManagedDependentResourceCustomResource> context) {
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
