package io.javaoperatorsdk.operator.sample;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

public abstract class AbstractExecutionNumberRecordingReconciler<P extends HasMetadata> implements
    Reconciler<P>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfReconcileExecutions = new AtomicInteger(0);

  protected int recordReconcileExecution() {
    return numberOfReconcileExecutions.incrementAndGet();
  }

  public int getNumberOfExecutions() {
    return numberOfReconcileExecutions.get();
  }

  @Override
  public UpdateControl<P> reconcile(P resource, Context<P> context) throws Exception {
    recordReconcileExecution();
    return UpdateControl.noUpdate();
  }
}
