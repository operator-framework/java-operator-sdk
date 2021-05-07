package io.javaoperatorsdk.operator.sample.event;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.TestExecutionInfoProvider;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class EventSourceTestCustomResourceController
    implements ResourceController<EventSourceTestCustomResource>, TestExecutionInfoProvider {

  public static final String FINALIZER_NAME =
      ControllerUtils.getDefaultFinalizerName(
          CustomResource.getCRDName(EventSourceTestCustomResource.class));
  private static final Logger log =
      LoggerFactory.getLogger(EventSourceTestCustomResourceController.class);
  public static final int TIMER_DELAY = 300;
  public static final int TIMER_PERIOD = 500;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final TimerEventSource timerEventSource = new TimerEventSource();

  @Override
  public void init(EventSourceManager eventSourceManager) {
    eventSourceManager.registerEventSource("Timer", timerEventSource);
  }

  @Override
  public UpdateControl<EventSourceTestCustomResource> createOrUpdateResource(
      EventSourceTestCustomResource resource, Context<EventSourceTestCustomResource> context) {

    timerEventSource.schedule(resource, TIMER_DELAY, TIMER_PERIOD);

    log.info("Events:: " + context.getEvents());
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
