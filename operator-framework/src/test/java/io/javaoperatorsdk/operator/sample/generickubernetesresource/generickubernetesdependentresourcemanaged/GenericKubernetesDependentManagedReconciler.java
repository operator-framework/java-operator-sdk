package io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentresourcemanaged;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.workflow.Workflow;

@ControllerConfiguration(
    workflow = @Workflow(
        dependents = {@Dependent(type = ConfigMapGenericKubernetesDependent.class)}))
public class GenericKubernetesDependentManagedReconciler
    implements Reconciler<GenericKubernetesDependentManagedCustomResource> {

  @Override
  public UpdateControl<GenericKubernetesDependentManagedCustomResource> reconcile(
      GenericKubernetesDependentManagedCustomResource resource,
      Context<GenericKubernetesDependentManagedCustomResource> context) {

    return UpdateControl.<GenericKubernetesDependentManagedCustomResource>noUpdate();
  }

}
