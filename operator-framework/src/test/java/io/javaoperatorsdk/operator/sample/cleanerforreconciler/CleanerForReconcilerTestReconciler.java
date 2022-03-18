package io.javaoperatorsdk.operator.sample.cleanerforreconciler;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class CleanerForReconcilerTestReconciler
    implements Reconciler<CleanerForReconcilerCustomResource>,
    Cleaner<CleanerForReconcilerCustomResource>,
    TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final AtomicInteger numberOfCleanupExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<CleanerForReconcilerCustomResource> reconcile(
      CleanerForReconcilerCustomResource resource,
      Context<CleanerForReconcilerCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public int getNumberOfCleanupExecutions() {
    return numberOfCleanupExecutions.get();
  }

  @Override
  public DeleteControl cleanup(CleanerForReconcilerCustomResource resource,
      Context<CleanerForReconcilerCustomResource> context) {
    numberOfCleanupExecutions.addAndGet(1);
    return DeleteControl.defaultDelete();
  }
}
