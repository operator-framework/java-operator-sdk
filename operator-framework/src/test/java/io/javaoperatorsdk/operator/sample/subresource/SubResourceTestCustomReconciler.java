package io.javaoperatorsdk.operator.sample.subresource;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.support.TestUtils.waitXms;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class SubResourceTestCustomReconciler
    implements Reconciler<SubResourceTestCustomResource>, TestExecutionInfoProvider {

  public static final String FINALIZER_NAME =
      ReconcilerUtils.getDefaultFinalizerName(SubResourceTestCustomResource.class);
  private static final Logger log =
      LoggerFactory.getLogger(SubResourceTestCustomReconciler.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);


  @Override
  public UpdateControl<SubResourceTestCustomResource> reconcile(
      SubResourceTestCustomResource resource, Context context) {
    numberOfExecutions.addAndGet(1);
    if (!resource.getMetadata().getFinalizers().contains(FINALIZER_NAME)) {
      throw new IllegalStateException("Finalizer is not present.");
    }
    log.info("Value: " + resource.getSpec().getValue());

    ensureStatusExists(resource);
    resource.getStatus().setState(SubResourceTestCustomResourceStatus.State.SUCCESS);
    waitXms(500);
    return UpdateControl.updateStatus(resource);
  }

  private void ensureStatusExists(SubResourceTestCustomResource resource) {
    SubResourceTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new SubResourceTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
