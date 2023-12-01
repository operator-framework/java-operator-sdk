package io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(
    dependents = {@Dependent(type = ConfigMapGenericKubernetesDependent.class)})
public class GenericKubernetesDependentManagedReconciler
    implements Reconciler<GenericKubernetesDependentManagedCustomResource> {

  @Override
  public UpdateControl<GenericKubernetesDependentManagedCustomResource> reconcile(
      GenericKubernetesDependentManagedCustomResource resource,
      Context<GenericKubernetesDependentManagedCustomResource> context) {

    return UpdateControl.<GenericKubernetesDependentManagedCustomResource>noUpdate();
  }

}
