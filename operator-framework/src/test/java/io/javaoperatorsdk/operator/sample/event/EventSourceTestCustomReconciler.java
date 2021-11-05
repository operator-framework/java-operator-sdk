package io.javaoperatorsdk.operator.sample.event;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Controller
public class EventSourceTestCustomReconciler
    implements Reconciler<EventSourceTestCustomResource>, EventSourceInitializer,
    TestExecutionInfoProvider {

  public static final String FINALIZER_NAME =
      ControllerUtils.getDefaultFinalizerName(
          CustomResource.getCRDName(EventSourceTestCustomResource.class));
  public static final int TIMER_PERIOD = 500;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final TimerEventSource<EventSourceTestCustomResource> timerEventSource =
      new TimerEventSource<>();

  @Override
  public void prepareEventSources(EventSourceRegistry eventSourceRegistry) {
    eventSourceRegistry.registerEventSource(timerEventSource);
  }

  @Override
  public UpdateControl<EventSourceTestCustomResource> reconcile(
      EventSourceTestCustomResource resource, Context context) {

    numberOfExecutions.addAndGet(1);
    ensureStatusExists(resource);
    resource.getStatus().setState(EventSourceTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.updateStatusSubResource(resource).rescheduleAfter(TIMER_PERIOD);
  }

  private void ensureStatusExists(EventSourceTestCustomResource resource) {
    EventSourceTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new EventSourceTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
