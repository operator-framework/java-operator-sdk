package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.KubernetesResourceUtils;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimerEventSource<R extends CustomResource<?, ?>> extends AbstractEventSource {
  private static final Logger log = LoggerFactory.getLogger(TimerEventSource.class);

  private final Timer timer = new Timer();
  private final AtomicBoolean running = new AtomicBoolean();
  private final Map<String, EventProducerTimeTask> onceTasks = new ConcurrentHashMap<>();
  private final Map<String, EventProducerTimeTask> timerTasks = new ConcurrentHashMap<>();

  public void schedule(R customResource, long delay, long period) {
    if (!running.get()) {
      throw new IllegalStateException("The TimerEventSource is not running");
    }

    String resourceUid = KubernetesResourceUtils.getUID(customResource);
    if (timerTasks.containsKey(resourceUid)) {
      return;
    }
    EventProducerTimeTask task = new EventProducerTimeTask(resourceUid);
    timerTasks.put(resourceUid, task);
    timer.schedule(task, delay, period);
  }

  public void scheduleOnce(R customResource, long delay) {
    if (!running.get()) {
      throw new IllegalStateException("The TimerEventSource is not running");
    }

    String resourceUid = KubernetesResourceUtils.getUID(customResource);
    if (onceTasks.containsKey(resourceUid)) {
      cancelOnceSchedule(resourceUid);
    }
    EventProducerTimeTask task = new EventProducerTimeTask(resourceUid);
    onceTasks.put(resourceUid, task);
    timer.schedule(task, delay);
  }

  @Override
  public void eventSourceDeRegisteredForResource(String customResourceUid) {
    cancelSchedule(customResourceUid);
    cancelOnceSchedule(customResourceUid);
  }

  public void cancelSchedule(String customResourceUid) {
    TimerTask timerTask = timerTasks.remove(customResourceUid);
    if (timerTask != null) {
      timerTask.cancel();
    }
  }

  public void cancelOnceSchedule(String customResourceUid) {
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
  public void close() throws IOException {
    running.set(false);
    onceTasks.keySet().forEach(this::cancelOnceSchedule);
    timerTasks.keySet().forEach(this::cancelSchedule);
    timer.cancel();
  }

  public class EventProducerTimeTask extends TimerTask {

    protected final String customResourceUid;

    public EventProducerTimeTask(String customResourceUid) {
      this.customResourceUid = customResourceUid;
    }

    @Override
    public void run() {
      if (running.get()) {
        log.debug("Producing event for custom resource id: {}", customResourceUid);
        eventHandler.handleEvent(new TimerEvent(customResourceUid, TimerEventSource.this));
      }
    }
  }
}
