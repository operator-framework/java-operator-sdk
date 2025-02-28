package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration
public class PatchResourceWithSSAReconciler
    implements Reconciler<PatchResourceWithSSACustomResource>,
        Cleaner<PatchResourceWithSSACustomResource> {

  public static final String ADDED_VALUE = "Added Value";

  @Override
  public UpdateControl<PatchResourceWithSSACustomResource> reconcile(
      PatchResourceWithSSACustomResource resource,
      Context<PatchResourceWithSSACustomResource> context) {

    var res = new PatchResourceWithSSACustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());

    // first update the spec with missing value, then status in next reconciliation
    if (resource.getSpec().getControllerManagedValue() == null) {
      res.setSpec(new PatchResourceWithSSASpec());
      res.getSpec().setControllerManagedValue(ADDED_VALUE);
      return UpdateControl.patchResource(res);
    } else {
      res.setStatus(new PatchResourceWithSSAStatus());
      res.getStatus().setSuccessfullyReconciled(true);
      return UpdateControl.patchStatus(res);
    }
  }

  @Override
  public DeleteControl cleanup(
      PatchResourceWithSSACustomResource resource,
      Context<PatchResourceWithSSACustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
