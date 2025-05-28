package io.javaoperatorsdk.operator.dependent.specialresourcesdependent;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.dependent.specialresourcesdependent.SpecialResourceSpec.INITIAL_VALUE;

@KubernetesDependent
public class ServiceAccountDependentResource
    extends CRUDKubernetesDependentResource<ServiceAccount, SpecialResourceCustomResource> {

  @Override
  protected ServiceAccount desired(
      SpecialResourceCustomResource primary, Context<SpecialResourceCustomResource> context) {
    ServiceAccount res = new ServiceAccount();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    res.setAutomountServiceAccountToken(INITIAL_VALUE.equals(primary.getSpec().getValue()));

    return res;
  }
}
