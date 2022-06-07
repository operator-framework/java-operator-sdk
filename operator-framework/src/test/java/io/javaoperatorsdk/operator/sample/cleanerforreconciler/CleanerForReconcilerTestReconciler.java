package io.javaoperatorsdk.operator.sample.cleanerforreconciler;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration
public class CleanerForReconcilerTestReconciler
    extends AbstractExecutionNumberRecordingReconciler<CleanerForReconcilerCustomResource>
    implements Cleaner<CleanerForReconcilerCustomResource> {

  private final AtomicInteger numberOfCleanupExecutions = new AtomicInteger(0);

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
