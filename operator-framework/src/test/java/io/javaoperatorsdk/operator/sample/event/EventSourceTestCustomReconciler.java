package io.javaoperatorsdk.operator.sample.event;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration
public class EventSourceTestCustomReconciler
    extends AbstractExecutionNumberRecordingReconciler<EventSourceTestCustomResource> {

  public static final int TIMER_PERIOD = 500;

  @Override
  public UpdateControl<EventSourceTestCustomResource> reconcile(
      EventSourceTestCustomResource resource, Context<EventSourceTestCustomResource> context) {

    recordReconcileExecution();
    ensureStatusExists(resource);
    resource.getStatus().setState(EventSourceTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.patchStatus(resource).rescheduleAfter(TIMER_PERIOD);
  }

  private void ensureStatusExists(EventSourceTestCustomResource resource) {
    EventSourceTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new EventSourceTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }
}
