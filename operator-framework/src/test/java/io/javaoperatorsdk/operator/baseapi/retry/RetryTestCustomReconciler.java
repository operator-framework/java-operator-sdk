package io.javaoperatorsdk.operator.baseapi.retry;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class RetryTestCustomReconciler
    implements Reconciler<RetryTestCustomResource>, TestExecutionInfoProvider {

  private static final Logger log = LoggerFactory.getLogger(RetryTestCustomReconciler.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final AtomicInteger numberOfExecutionFails;

  public RetryTestCustomReconciler(int numberOfExecutionFails) {
    this.numberOfExecutionFails = new AtomicInteger(numberOfExecutionFails);
  }

  @Override
  public UpdateControl<RetryTestCustomResource> reconcile(
      RetryTestCustomResource resource, Context<RetryTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    log.info("Value: " + resource.getSpec().getValue());

    if (numberOfExecutions.get() < numberOfExecutionFails.get() + 1) {
      throw new RuntimeException("Testing Retry");
    }
    if (context.getRetryInfo().isEmpty() || context.getRetryInfo().get().isLastAttempt()) {
      throw new IllegalStateException("Not expected retry info: " + context.getRetryInfo());
    }

    ensureStatusExists(resource);
    resource.getStatus().setState(RetryTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.patchStatus(resource);
  }

  private void ensureStatusExists(RetryTestCustomResource resource) {
    RetryTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new RetryTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
