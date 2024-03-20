package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.util.concurrent.atomic.AtomicInteger;

@Workflow(dependents = @Dependent(readyPostcondition = SampleBulkCondition.class,
    type = CRUDConfigMapBulkDependentResource.class))
@ControllerConfiguration()
public class ManagedBulkDependentWithReadyConditionReconciler
    implements Reconciler<BulkDependentTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource,
      Context<BulkDependentTestCustomResource> context) throws Exception {
    numberOfExecutions.incrementAndGet();

    var ready = context.managedWorkflowAndDependentResourceContext().getWorkflowReconcileResult()
        .allDependentResourcesReady();


    resource.setStatus(new BulkDependentTestStatus());
    resource.getStatus().setReady(ready);

    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
