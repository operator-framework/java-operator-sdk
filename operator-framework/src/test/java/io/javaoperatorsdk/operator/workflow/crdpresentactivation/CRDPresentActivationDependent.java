package io.javaoperatorsdk.operator.workflow.crdpresentactivation;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class CRDPresentActivationDependent
    extends CRUDNoGCKubernetesDependentResource<
        CRDPresentActivationDependentCustomResource, CRDPresentActivationCustomResource> {

  @Override
  protected CRDPresentActivationDependentCustomResource desired(
      CRDPresentActivationCustomResource primary,
      Context<CRDPresentActivationCustomResource> context) {
    var res = new CRDPresentActivationDependentCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    return res;
  }
}
