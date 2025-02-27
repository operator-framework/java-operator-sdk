package io.javaoperatorsdk.operator.dependent.bulkdependent.readonly;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestStatus;

@Workflow(
    dependents =
        @Dependent(
            type = ReadOnlyBulkDependentResource.class,
            readyPostcondition = ReadOnlyBulkReadyPostCondition.class))
@ControllerConfiguration
public class ReadOnlyBulkReconciler implements Reconciler<BulkDependentTestCustomResource> {
  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context) {

    var nonReadyDependents =
        context
            .managedWorkflowAndDependentResourceContext()
            .getWorkflowReconcileResult()
            .orElseThrow()
            .getNotReadyDependents();

    BulkDependentTestCustomResource customResource = new BulkDependentTestCustomResource();
    customResource.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    var status = new BulkDependentTestStatus();
    status.setReady(nonReadyDependents.isEmpty());
    customResource.setStatus(status);

    return UpdateControl.patchStatus(customResource);
  }
}
