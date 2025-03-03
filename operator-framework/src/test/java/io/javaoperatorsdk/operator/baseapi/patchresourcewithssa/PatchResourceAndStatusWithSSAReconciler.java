package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration
public class PatchResourceAndStatusWithSSAReconciler
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

    res.setSpec(new PatchResourceWithSSASpec());
    res.getSpec().setControllerManagedValue(ADDED_VALUE);
    res.setStatus(new PatchResourceWithSSAStatus());
    res.getStatus().setSuccessfullyReconciled(true);

    return UpdateControl.patchResourceAndStatus(res);
  }

  @Override
  public DeleteControl cleanup(
      PatchResourceWithSSACustomResource resource,
      Context<PatchResourceWithSSACustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
