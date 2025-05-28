package io.javaoperatorsdk.operator.baseapi.statuspatchnonlocking;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class StatusPatchLockingReconciler implements Reconciler<StatusPatchLockingCustomResource> {

  public static final String MESSAGE = "message";
  public static final long WAIT_TIME = 500L;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<StatusPatchLockingCustomResource> reconcile(
      StatusPatchLockingCustomResource resource, Context<StatusPatchLockingCustomResource> context)
      throws InterruptedException {
    numberOfExecutions.addAndGet(1);
    Thread.sleep(WAIT_TIME);

    if (resource.getStatus() == null) {
      resource.setStatus(new StatusPatchLockingCustomResourceStatus());
    }
    resource.getStatus().setMessage(resource.getSpec().isMessageInStatus() ? MESSAGE : null);
    resource.getStatus().setValue(resource.getStatus().getValue() + 1);
    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
