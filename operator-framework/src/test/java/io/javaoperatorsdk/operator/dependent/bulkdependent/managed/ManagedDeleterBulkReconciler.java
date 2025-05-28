package io.javaoperatorsdk.operator.dependent.bulkdependent.managed;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource;

@Workflow(dependents = @Dependent(type = ConfigMapDeleterBulkDependentResource.class))
@ControllerConfiguration
public class ManagedDeleterBulkReconciler implements Reconciler<BulkDependentTestCustomResource> {
  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context)
      throws Exception {

    return UpdateControl.noUpdate();
  }
}
