package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentresourcemanaged;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = ConfigMapGenericKubernetesDependent.class)})
@ControllerConfiguration
public class GenericKubernetesDependentManagedReconciler
    implements Reconciler<GenericKubernetesDependentManagedCustomResource> {

  @Override
  public UpdateControl<GenericKubernetesDependentManagedCustomResource> reconcile(
      GenericKubernetesDependentManagedCustomResource resource,
      Context<GenericKubernetesDependentManagedCustomResource> context) {

    return UpdateControl.noUpdate();
  }
}
