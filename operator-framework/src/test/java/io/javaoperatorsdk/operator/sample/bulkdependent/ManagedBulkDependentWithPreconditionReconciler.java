package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(dependents = @Dependent(reconcilePrecondition = SamplePrecondition.class,
    type = CRUDConfigMapBulkDependentResource.class))
public class ManagedBulkDependentWithPreconditionReconciler
    implements Reconciler<BulkDependentTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource,
      Context<BulkDependentTestCustomResource> context) throws Exception {
    numberOfExecutions.incrementAndGet();
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
