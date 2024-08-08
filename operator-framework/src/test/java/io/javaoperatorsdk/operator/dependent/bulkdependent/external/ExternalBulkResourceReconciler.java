package io.javaoperatorsdk.operator.dependent.bulkdependent.external;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;

@Workflow(dependents = @Dependent(type = ExternalBulkDependentResource.class))
@ControllerConfiguration()
public class ExternalBulkResourceReconciler implements Reconciler<BulkDependentTestCustomResource> {

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context)
      throws Exception {
    return UpdateControl.noUpdate();
  }
}
