package io.javaoperatorsdk.operator.sample.specialresourcesdependent;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.sample.specialresourcesdependent.SpecialResourceSpec.INITIAL_VALUE;

@KubernetesDependent
public class ServiceAccountDependentResource extends
    CRUDKubernetesDependentResource<ServiceAccount, SpecialResourceCustomResource> {

  public ServiceAccountDependentResource() {
    super(ServiceAccount.class);
  }

  @Override
  protected ServiceAccount desired(SpecialResourceCustomResource primary,
      Context<SpecialResourceCustomResource> context) {
    ServiceAccount res = new ServiceAccount();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    res.setAutomountServiceAccountToken(INITIAL_VALUE.equals(primary.getSpec().getValue()));

    return res;
  }

}
