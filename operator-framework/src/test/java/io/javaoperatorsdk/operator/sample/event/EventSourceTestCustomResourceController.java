package io.javaoperatorsdk.operator.sample.event;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Controller
public class EventSourceTestCustomResourceController
    implements ResourceController<EventSourceTestCustomResource>, EventSourceInitializer,
    TestExecutionInfoProvider {

  public static final String FINALIZER_NAME =
      ControllerUtils.getDefaultFinalizerName(
          CustomResource.getCRDName(EventSourceTestCustomResource.class));
  private static final Logger log =
      LoggerFactory.getLogger(EventSourceTestCustomResourceController.class);
  public static final int TIMER_DELAY = 300;
  public static final int TIMER_PERIOD = 500;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final TimerEventSource<EventSourceTestCustomResource> timerEventSource =
      new TimerEventSource<>();

  @Override
  public void prepareEventSources(EventSourceManager eventSourceManager) {
    eventSourceManager.registerEventSource(timerEventSource);
  }

  @Override
  public UpdateControl<EventSourceTestCustomResource> createOrUpdateResource(
      EventSourceTestCustomResource resource, Context context) {

    timerEventSource.schedule(resource, TIMER_DELAY, TIMER_PERIOD);

    numberOfExecutions.addAndGet(1);
    ensureStatusExists(resource);
    resource.getStatus().setState(EventSourceTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.updateStatusSubResource(resource);
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
