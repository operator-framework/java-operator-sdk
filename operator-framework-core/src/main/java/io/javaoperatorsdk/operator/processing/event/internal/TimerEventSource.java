package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

public class TimerEventSource<R extends HasMetadata> extends AbstractEventSource {
  private static final Logger log = LoggerFactory.getLogger(TimerEventSource.class);

  private final Timer timer = new Timer();
  private final AtomicBoolean running = new AtomicBoolean();
  private final Map<CustomResourceID, EventProducerTimeTask> onceTasks = new ConcurrentHashMap<>();


  public void scheduleOnce(R customResource, long delay) {
    if (!running.get()) {
      throw new IllegalStateException("The TimerEventSource is not running");
    }
    CustomResourceID resourceUid = CustomResourceID.fromResource(customResource);
    if (onceTasks.containsKey(resourceUid)) {
      cancelOnceSchedule(resourceUid);
    }
    EventProducerTimeTask task = new EventProducerTimeTask(resourceUid);
    onceTasks.put(resourceUid, task);
    timer.schedule(task, delay);
  }

  @Override
  public void cleanupForCustomResource(CustomResourceID customResourceUid) {
    cancelOnceSchedule(customResourceUid);
  }

  public void cancelOnceSchedule(CustomResourceID customResourceUid) {
    TimerTask timerTask = onceTasks.remove(customResourceUid);
    if (timerTask != null) {
      timerTask.cancel();
    }
  }

  @Override
  public void start() {
    running.set(true);
  }

  @Override
  public void stop() {
    running.set(false);
    onceTasks.keySet().forEach(this::cancelOnceSchedule);
    timer.cancel();
  }

  public class EventProducerTimeTask extends TimerTask {

    protected final CustomResourceID customResourceUid;

    public EventProducerTimeTask(CustomResourceID customResourceUid) {
      this.customResourceUid = customResourceUid;
    }

    @Override
    public void run() {
      if (running.get()) {
        log.debug("Producing event for custom resource id: {}", customResourceUid);
        eventHandler.handleEvent(new Event(customResourceUid));
      }
    }
  }
}
