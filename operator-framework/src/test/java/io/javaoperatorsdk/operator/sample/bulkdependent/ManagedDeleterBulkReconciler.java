package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.workflow.Workflow;

@ControllerConfiguration(
    workflow = @Workflow(
        dependents = @Dependent(type = ConfigMapDeleterBulkDependentResource.class)))
public class ManagedDeleterBulkReconciler implements Reconciler<BulkDependentTestCustomResource> {
  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource,
      Context<BulkDependentTestCustomResource> context)
      throws Exception {

    return UpdateControl.noUpdate();
  }
}
