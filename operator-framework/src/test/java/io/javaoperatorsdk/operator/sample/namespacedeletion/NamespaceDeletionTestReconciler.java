package io.javaoperatorsdk.operator.sample.namespacedeletion;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class NamespaceDeletionTestReconciler
    implements Reconciler<NamespaceDeletionTestCustomResource>, TestExecutionInfoProvider,
    Cleaner<NamespaceDeletionTestCustomResource> {

  public static final int CLEANER_WAIT_PERIOD = 300;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<NamespaceDeletionTestCustomResource> reconcile(
      NamespaceDeletionTestCustomResource resource,
      Context<NamespaceDeletionTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public DeleteControl cleanup(NamespaceDeletionTestCustomResource resource,
      Context<NamespaceDeletionTestCustomResource> context) {
    try {
      Thread.sleep(CLEANER_WAIT_PERIOD);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
    return DeleteControl.defaultDelete();
  }
}
