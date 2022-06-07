package io.javaoperatorsdk.operator.sample.subresource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

import static io.javaoperatorsdk.operator.support.TestUtils.waitXms;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class SubResourceTestCustomReconciler
    extends AbstractExecutionNumberRecordingReconciler<SubResourceTestCustomResource> {

  public static final int RECONCILER_MIN_EXEC_TIME = 300;

  private static final Logger log =
      LoggerFactory.getLogger(SubResourceTestCustomReconciler.class);

  @Override
  public UpdateControl<SubResourceTestCustomResource> reconcile(
      SubResourceTestCustomResource resource, Context<SubResourceTestCustomResource> context) {
    recordReconcileExecution();
    log.info("Value: " + resource.getSpec().getValue());

    ensureStatusExists(resource);
    resource.getStatus().setState(SubResourceTestCustomResourceStatus.State.SUCCESS);
    waitXms(RECONCILER_MIN_EXEC_TIME);
    return UpdateControl.updateStatus(resource);
  }

  private void ensureStatusExists(SubResourceTestCustomResource resource) {
    SubResourceTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new SubResourceTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }
}
