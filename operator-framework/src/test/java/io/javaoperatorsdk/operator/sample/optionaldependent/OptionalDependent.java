package io.javaoperatorsdk.operator.sample.optionaldependent;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class OptionalDependent extends
    CRUDKubernetesDependentResource<OptionalDependentSecondaryCustomResource, OptionalDependentCustomResource> {

  public OptionalDependent() {
    super(OptionalDependentSecondaryCustomResource.class);
  }

  @Override
  protected OptionalDependentSecondaryCustomResource desired(
      OptionalDependentCustomResource primary,
      Context<OptionalDependentCustomResource> context) {
    var res = new OptionalDependentSecondaryCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    return res;
  }
}
