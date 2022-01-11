package io.javaoperatorsdk.operator.sample.retry;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class RetryTestCustomReconciler
    implements Reconciler<RetryTestCustomResource>, TestExecutionInfoProvider {

  public static final String FINALIZER_NAME =
      ReconcilerUtils.getDefaultFinalizerName(
          CustomResource.getCRDName(RetryTestCustomResource.class));
  private static final Logger log =
      LoggerFactory.getLogger(RetryTestCustomReconciler.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final AtomicInteger numberOfExecutionFails;


  public RetryTestCustomReconciler(int numberOfExecutionFails) {
    this.numberOfExecutionFails = new AtomicInteger(numberOfExecutionFails);
  }

  @Override
  public UpdateControl<RetryTestCustomResource> reconcile(RetryTestCustomResource resource,
      Context context) {
    numberOfExecutions.addAndGet(1);

    if (!resource.getMetadata().getFinalizers().contains(FINALIZER_NAME)) {
      throw new IllegalStateException("Finalizer is not present.");
    }
    log.info("Value: " + resource.getSpec().getValue());

    if (numberOfExecutions.get() < numberOfExecutionFails.get() + 1) {
      throw new RuntimeException("Testing Retry");
    }
    if (context.getRetryInfo().isEmpty() || context.getRetryInfo().get().isLastAttempt()) {
      throw new IllegalStateException("Not expected retry info: " + context.getRetryInfo());
    }

    ensureStatusExists(resource);
    resource.getStatus().setState(RetryTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.updateStatus(resource);
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
