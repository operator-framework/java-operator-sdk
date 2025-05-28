package io.javaoperatorsdk.operator.baseapi.nextreconciliationimminent;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class NextReconciliationImminentReconciler
    implements Reconciler<NextReconciliationImminentCustomResource> {

  private static final Logger log =
      LoggerFactory.getLogger(NextReconciliationImminentReconciler.class);

  private final SynchronousQueue<Boolean> queue = new SynchronousQueue<>();
  private volatile boolean reconciliationWaiting = false;

  @Override
  public UpdateControl<NextReconciliationImminentCustomResource> reconcile(
      NextReconciliationImminentCustomResource resource,
      Context<NextReconciliationImminentCustomResource> context)
      throws InterruptedException {
    log.info("started reconciliation");
    reconciliationWaiting = true;
    // wait long enough to get manually allowed
    queue.poll(120, TimeUnit.SECONDS);
    log.info("Continue after wait");
    reconciliationWaiting = false;

    if (context.isNextReconciliationImminent()) {
      return UpdateControl.noUpdate();
    } else {
      if (resource.getStatus() == null) {
        resource.setStatus(new NextReconciliationImminentStatus());
      }
      resource.getStatus().setUpdateNumber(resource.getStatus().getUpdateNumber() + 1);
      log.info("Patching status");
      return UpdateControl.patchStatus(resource);
    }
  }

  public void allowReconciliationToProceed() {
    try {
      queue.put(true);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isReconciliationWaiting() {
    return reconciliationWaiting;
  }
}
