package io.javaoperatorsdk.operator.dependent.bulkdependent.managed;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.CRUDConfigMapBulkDependentResource;

@Workflow(dependents = @Dependent(type = CRUDConfigMapBulkDependentResource.class))
@ControllerConfiguration
public class ManagedBulkDependentReconciler implements Reconciler<BulkDependentTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context)
      throws Exception {

    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }
}
