package io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentresourcemanaged;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = ConfigMapGenericKubernetesDependent.class)})
@ControllerConfiguration
public class GenericKubernetesDependentManagedReconciler
    implements Reconciler<GenericKubernetesDependentManagedCustomResource> {

  @Override
  public UpdateControl<GenericKubernetesDependentManagedCustomResource> reconcile(
      GenericKubernetesDependentManagedCustomResource resource,
      Context<GenericKubernetesDependentManagedCustomResource> context) {

    return UpdateControl.<GenericKubernetesDependentManagedCustomResource>noUpdate();
  }

}
