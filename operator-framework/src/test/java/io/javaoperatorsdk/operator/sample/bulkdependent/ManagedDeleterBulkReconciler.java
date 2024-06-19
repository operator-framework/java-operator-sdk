package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = @Dependent(type = ConfigMapDeleterBulkDependentResource.class))
@ControllerConfiguration
public class ManagedDeleterBulkReconciler implements Reconciler<BulkDependentTestCustomResource> {
  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource,
      Context<BulkDependentTestCustomResource> context)
      throws Exception {

    return UpdateControl.noUpdate();
  }
}
