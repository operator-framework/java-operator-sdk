package io.javaoperatorsdk.operator.processing.event.source.timer;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

public class TimerEventSource<R extends HasMetadata>
    extends AbstractEventSource
    implements ResourceEventAware<R> {
  private static final Logger log = LoggerFactory.getLogger(TimerEventSource.class);

  private Timer timer;
  private final Map<ResourceID, EventProducerTimeTask> onceTasks = new ConcurrentHashMap<>();

  @SuppressWarnings("unused")
  public void scheduleOnce(R resource, long delay) {
    scheduleOnce(ResourceID.fromResource(resource), delay);
  }

  public void scheduleOnce(ResourceID resourceID, long delay) {
    if (!isRunning()) {
      throw new IllegalStateException("The TimerEventSource is not running");
    }

    if (onceTasks.containsKey(resourceID)) {
      cancelOnceSchedule(resourceID);
    }
    EventProducerTimeTask task = new EventProducerTimeTask(resourceID);
    onceTasks.put(resourceID, task);
    timer.schedule(task, delay);
  }

  @Override
  public void onResourceDeleted(R resource) {
    cancelOnceSchedule(ResourceID.fromResource(resource));
  }

  public void cancelOnceSchedule(ResourceID customResourceUid) {
    TimerTask timerTask = onceTasks.remove(customResourceUid);
    if (timerTask != null) {
      timerTask.cancel();
    }
  }

  @Override
  public void start() {
    if (!isRunning()) {
      super.start();
      timer = new Timer(true);
    }
  }

  @Override
  public void stop() {
    if (isRunning()) {
      onceTasks.keySet().forEach(this::cancelOnceSchedule);
      timer.cancel();
      super.stop();
    }
  }

  public class EventProducerTimeTask extends TimerTask {

    protected final ResourceID customResourceUid;

    public EventProducerTimeTask(ResourceID customResourceUid) {
      this.customResourceUid = customResourceUid;
    }

    @Override
    public void run() {
      if (isRunning()) {
        log.debug("Producing event for custom resource id: {}", customResourceUid);
        getEventHandler().handleEvent(new Event(customResourceUid));
      }
    }
  }
}
