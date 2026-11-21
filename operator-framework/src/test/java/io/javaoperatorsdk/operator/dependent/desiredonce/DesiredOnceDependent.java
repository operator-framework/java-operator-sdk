package io.javaoperatorsdk.operator.dependent.desiredonce;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(informer = @Informer(labelSelector = "desiredonce=true"))
public class DesiredOnceDependent extends CRUDKubernetesDependentResource<ConfigMap, DesiredOnce> {
  private boolean desiredAlreadyCalled;

  @Override
  protected ConfigMap desired(DesiredOnce primary, Context<DesiredOnce> context) {
    if (desiredAlreadyCalled) {
      throw new IllegalStateException("desired should have only been called once");
    }
    desiredAlreadyCalled = true;
    return new ConfigMapBuilder()
        .editOrNewMetadata()
        .withName(getName(primary))
        .withNamespace(primary.getMetadata().getNamespace())
        .withLabels(Map.of("desiredonce", "true"))
        .endMetadata()
        .addToData(DesiredOnce.KEY, primary.getSpec().value())
        .build();
  }

  static String getName(DesiredOnce primary) {
    return primary.getMetadata().getName() + "cm";
  }
}
