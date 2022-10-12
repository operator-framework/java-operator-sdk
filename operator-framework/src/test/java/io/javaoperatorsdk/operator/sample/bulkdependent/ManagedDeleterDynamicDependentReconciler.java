package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(
    dependents = @Dependent(type = ConfigMapDeleterDynamicallyCreatedDependentResource.class))
public class ManagedDeleterDynamicDependentReconciler
    implements Reconciler<DynamicDependentTestCustomResource> {
  @Override
  public UpdateControl<DynamicDependentTestCustomResource> reconcile(
      DynamicDependentTestCustomResource resource,
      Context<DynamicDependentTestCustomResource> context)
      throws Exception {

    return UpdateControl.noUpdate();
  }
}
