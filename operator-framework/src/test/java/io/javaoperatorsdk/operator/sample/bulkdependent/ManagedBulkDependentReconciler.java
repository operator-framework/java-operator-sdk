package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.util.concurrent.atomic.AtomicInteger;

@Workflow(dependents = @Dependent(type = CRUDConfigMapBulkDependentResource.class))
@ControllerConfiguration
public class ManagedBulkDependentReconciler
    implements Reconciler<BulkDependentTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource,
      Context<BulkDependentTestCustomResource> context) throws Exception {

    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }
}
