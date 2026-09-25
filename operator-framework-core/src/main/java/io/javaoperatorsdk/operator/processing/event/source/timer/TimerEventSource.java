/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event.source.timer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.BaseControl;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

public class TimerEventSource<R extends HasMetadata> extends AbstractEventSource<Void, HasMetadata>
    implements ResourceEventAware<R> {
  private static final Logger log = LoggerFactory.getLogger(TimerEventSource.class);

  private final Map<ResourceID, ScheduledFuture<?>> onceTasks = new ConcurrentHashMap<>();
  private final Supplier<ScheduledExecutorService> executorServiceSupplier;
  private final boolean ownsExecutorService;
  private boolean triggerReconcilerOnAllEvents;
  private volatile ScheduledExecutorService executorService;

  public TimerEventSource() {
    this((Supplier<ScheduledExecutorService>) null);
  }

  public TimerEventSource(String name, boolean triggerReconcilerOnAllEvents) {
    this(name, triggerReconcilerOnAllEvents, null);
  }

  /**
   * Creates an event source scheduling on the executor provided by the specified supplier. The
   * supplier is called on every start, and not once at creation time, since the operator shuts its
   * executors down when it is stopped and creates new ones if it is started again.
   *
   * @param executorServiceSupplier supplies the executor to schedule on, {@code null} to have the
   *     event source create, and shut down, an executor of its own
   * @since 5.6.0
   */
  public TimerEventSource(Supplier<ScheduledExecutorService> executorServiceSupplier) {
    super(Void.class);
    this.executorServiceSupplier = executorServiceSupplier;
    this.ownsExecutorService = executorServiceSupplier == null;
  }

  /**
   * @see #TimerEventSource(Supplier)
   * @since 5.6.0
   */
  public TimerEventSource(
      String name,
      boolean triggerReconcilerOnAllEvents,
      Supplier<ScheduledExecutorService> executorServiceSupplier) {
    super(Void.class, name);
    this.triggerReconcilerOnAllEvents = triggerReconcilerOnAllEvents;
    this.executorServiceSupplier = executorServiceSupplier;
    this.ownsExecutorService = executorServiceSupplier == null;
  }

  @SuppressWarnings("unused")
  public void scheduleOnce(R resource, long delay) {
    scheduleOnce(ResourceID.fromResource(resource), delay);
  }

  public void scheduleOnce(ResourceID resourceID, long delay) {
    final var executor = executorService;
    if (!isRunning() || executor == null) {
      throw new IllegalStateException("The TimerEventSource is not running");
    }

    if (delay == BaseControl.INSTANT_RESCHEDULE) {
      cancelOnceSchedule(resourceID);
      new EventProducerTimeTask(resourceID).run();
      return;
    }

    onceTasks.compute(
        resourceID,
        (id, alreadyScheduled) -> {
          if (alreadyScheduled != null) {
            alreadyScheduled.cancel(false);
          }
          return executor.schedule(new EventProducerTimeTask(id), delay, TimeUnit.MILLISECONDS);
        });
  }

  @Override
  public void onResourceDeleted(R resource) {
    // for triggerReconcilerOnAllEvents the cancelOnceSchedule will be called on
    // successful delete event processing
    if (!triggerReconcilerOnAllEvents) {
      cancelOnceSchedule(ResourceID.fromResource(resource));
    }
  }

  public void cancelOnceSchedule(ResourceID customResourceUid) {
    var scheduled = onceTasks.remove(customResourceUid);
    if (scheduled != null) {
      // as with the java.util.TimerTask this replaces, a task that is already running is left to
      // finish
      scheduled.cancel(false);
    }
  }

  @Override
  public void start() {
    if (!isRunning()) {
      super.start();
      executorService = resolveExecutorService();
    }
  }

  private ScheduledExecutorService resolveExecutorService() {
    if (executorServiceSupplier != null) {
      return executorServiceSupplier.get();
    }
    return Executors.newSingleThreadScheduledExecutor(
        Utils.daemonThreadFactory("josdk-timer-" + name()));
  }

  @Override
  public void stop() {
    if (isRunning()) {
      onceTasks.keySet().forEach(this::cancelOnceSchedule);
      super.stop();
      if (ownsExecutorService && executorService != null) {
        executorService.shutdownNow();
      }
    }
  }

  @Override
  public Status getStatus() {
    return isRunning() ? Status.HEALTHY : Status.UNHEALTHY;
  }

  @Override
  public Set<Void> getSecondaryResources(HasMetadata primary) {
    return Set.of();
  }

  public class EventProducerTimeTask implements Runnable {

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
