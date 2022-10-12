package io.javaoperatorsdk.operator.sample.bulkdependent.external;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.bulkdependent.DynamicDependentTestCustomResource;

@ControllerConfiguration(
    dependents = @Dependent(type = ExternalDynamicallyCreatedDependentResource.class))
public class ExternalDynamicResourceReconciler
    implements Reconciler<DynamicDependentTestCustomResource> {

  @Override
  public UpdateControl<DynamicDependentTestCustomResource> reconcile(
      DynamicDependentTestCustomResource resource,
      Context<DynamicDependentTestCustomResource> context)
      throws Exception {
    return UpdateControl.noUpdate();
  }
}
