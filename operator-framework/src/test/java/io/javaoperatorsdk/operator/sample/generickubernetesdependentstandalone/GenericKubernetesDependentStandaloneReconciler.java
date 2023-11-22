package io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.sample.bulkdependent.CRUDConfigMapBulkDependentResource;
import io.javaoperatorsdk.operator.sample.bulkdependent.ConfigMapDeleterBulkDependentResource;

@ControllerConfiguration
public class GenericKubernetesDependentStandaloneReconciler
    implements Reconciler<GenericKubernetesDependentStandaloneCustomResource> {


  private final ConfigMapDeleterBulkDependentResource dependent;

  public GenericKubernetesDependentStandaloneReconciler() {
    dependent = new CRUDConfigMapBulkDependentResource();
  }

  @Override
  public UpdateControl<GenericKubernetesDependentStandaloneCustomResource> reconcile(
      GenericKubernetesDependentStandaloneCustomResource resource,
      Context<GenericKubernetesDependentStandaloneCustomResource> context) {



    return UpdateControl.noUpdate();
  }

}
