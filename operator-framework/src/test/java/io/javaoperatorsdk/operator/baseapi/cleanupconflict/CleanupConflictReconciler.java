package io.javaoperatorsdk.operator.baseapi.cleanupconflict;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration
public class CleanupConflictReconciler
    implements Reconciler<CleanupConflictCustomResource>, Cleaner<CleanupConflictCustomResource> {

  public static final long WAIT_TIME = 500L;
  private final AtomicInteger numberOfCleanupExecutions = new AtomicInteger(0);
  private final AtomicInteger numberReconcileExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<CleanupConflictCustomResource> reconcile(
      CleanupConflictCustomResource resource, Context<CleanupConflictCustomResource> context) {
    numberReconcileExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      CleanupConflictCustomResource resource, Context<CleanupConflictCustomResource> context) {
    numberOfCleanupExecutions.addAndGet(1);
    try {
      Thread.sleep(WAIT_TIME);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return DeleteControl.defaultDelete();
  }

  public int getNumberOfCleanupExecutions() {
    return numberOfCleanupExecutions.intValue();
  }

  public int getNumberReconcileExecutions() {
    return numberReconcileExecutions.intValue();
  }
}
