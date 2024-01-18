package io.javaoperatorsdk.operator.sample.bulkdependent.external;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.workflow.Workflow;
import io.javaoperatorsdk.operator.sample.bulkdependent.BulkDependentTestCustomResource;

@ControllerConfiguration(
    workflow = @Workflow(dependents = @Dependent(type = ExternalBulkDependentResource.class)))
public class ExternalBulkResourceReconciler implements Reconciler<BulkDependentTestCustomResource> {

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context)
      throws Exception {
    return UpdateControl.noUpdate();
  }
}
