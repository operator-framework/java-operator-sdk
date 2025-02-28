package io.javaoperatorsdk.operator.dependent.bulkdependent.condition;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestStatus;
import io.javaoperatorsdk.operator.dependent.bulkdependent.CRUDConfigMapBulkDependentResource;

@Workflow(
    dependents =
        @Dependent(
            readyPostcondition = SampleBulkCondition.class,
            type = CRUDConfigMapBulkDependentResource.class))
@ControllerConfiguration()
public class ManagedBulkDependentWithReadyConditionReconciler
    implements Reconciler<BulkDependentTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context)
      throws Exception {
    numberOfExecutions.incrementAndGet();

    var ready =
        context
            .managedWorkflowAndDependentResourceContext()
            .getWorkflowReconcileResult()
            .orElseThrow()
            .allDependentResourcesReady();

    resource.setStatus(new BulkDependentTestStatus());
    resource.getStatus().setReady(ready);

    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
