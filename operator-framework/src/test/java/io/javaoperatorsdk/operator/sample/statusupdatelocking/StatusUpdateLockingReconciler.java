package io.javaoperatorsdk.operator.sample.statusupdatelocking;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration
public class StatusUpdateLockingReconciler
    implements Reconciler<StatusUpdateLockingCustomResource> {

  public static final long WAIT_TIME = 500L;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<StatusUpdateLockingCustomResource> reconcile(
      StatusUpdateLockingCustomResource resource,
      Context<StatusUpdateLockingCustomResource> context)
      throws InterruptedException {
    numberOfExecutions.addAndGet(1);
    Thread.sleep(WAIT_TIME);
    resource.getStatus().setValue(resource.getStatus().getValue() + 1);
    return UpdateControl.updateStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
