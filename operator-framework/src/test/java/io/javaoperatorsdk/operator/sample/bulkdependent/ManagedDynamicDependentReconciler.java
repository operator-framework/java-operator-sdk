package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(
    dependents = @Dependent(type = CRUDConfigMapDynamicallyCreatedDependentResource.class))
public class ManagedDynamicDependentReconciler
    implements Reconciler<DynamicDependentTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<DynamicDependentTestCustomResource> reconcile(
      DynamicDependentTestCustomResource resource,
      Context<DynamicDependentTestCustomResource> context) throws Exception {

    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }
}
