package io.javaoperatorsdk.operator.sample.gracefulstop;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.concurrent.atomic.AtomicInteger;

@ControllerConfiguration
public class GracefulStopTestReconciler
    implements Reconciler<GracefulStopTestCustomResource> {

  public static final int RECONCILER_SLEEP = 1000;

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<GracefulStopTestCustomResource> reconcile(
      GracefulStopTestCustomResource resource,
      Context<GracefulStopTestCustomResource> context) throws InterruptedException {

    numberOfExecutions.addAndGet(1);
    resource.setStatus(new GracefulStopTestCustomResourceStatus());
    Thread.sleep(RECONCILER_SLEEP);

    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
