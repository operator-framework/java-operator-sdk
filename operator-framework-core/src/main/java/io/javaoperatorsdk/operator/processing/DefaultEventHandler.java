package io.javaoperatorsdk.operator.processing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.Metrics;
import io.javaoperatorsdk.operator.api.RetryInfo;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

import static io.javaoperatorsdk.operator.EventListUtils.containsCustomResourceDeletedEvent;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;

/**
 * Event handler that makes sure that events are processed in a "single threaded" way per resource
 * UID, while buffering events which are received during an execution.
 */
public class DefaultEventHandler<R extends CustomResource<?, ?>> implements EventHandler {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventHandler.class);

  @Deprecated
  private static EventMonitor monitor = EventMonitor.NOOP;

  private final EventBuffer eventBuffer;
  private final Set<CustomResourceID> underProcessing = new HashSet<>();
  private final EventDispatcher<R> eventDispatcher;
  private final Retry retry;
  private final Map<CustomResourceID, RetryExecution> retryState = new HashMap<>();
  private final ExecutorService executor;
  private final String controllerName;
  private final ReentrantLock lock = new ReentrantLock();
  private final EventMonitor eventMonitor;
  private volatile boolean running;
  private final ResourceCache<R> resourceCache;
  private DefaultEventSourceManager<R> eventSourceManager;

  public DefaultEventHandler(ConfiguredController<R> controller, ResourceCache<R> resourceCache) {
    this(
        resourceCache,
        ExecutorServiceManager.instance().executorService(),
        controller.getConfiguration().getName(),
        new EventDispatcher<>(controller),
        GenericRetry.fromConfiguration(controller.getConfiguration().getRetryConfiguration()),
        controller.getConfiguration().getConfigurationService().getMetrics().getEventMonitor());
  }

  DefaultEventHandler(EventDispatcher<R> eventDispatcher, ResourceCache<R> resourceCache,
      String relatedControllerName,
      Retry retry) {
    this(resourceCache, null, relatedControllerName, eventDispatcher, retry, null);
  }

  private DefaultEventHandler(ResourceCache<R> resourceCache, ExecutorService executor,
      String relatedControllerName,
      EventDispatcher<R> eventDispatcher, Retry retry, EventMonitor monitor) {
    this.running = true;
    this.executor =
        executor == null
            ? new ScheduledThreadPoolExecutor(
                ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER)
            : executor;
    this.controllerName = relatedControllerName;
    this.eventDispatcher = eventDispatcher;
    this.retry = retry;
    eventBuffer = new EventBuffer();
    this.resourceCache = resourceCache;
    this.eventMonitor = monitor != null ? monitor : EventMonitor.NOOP;
  }

  public void setEventSourceManager(DefaultEventSourceManager<R> eventSourceManager) {
    this.eventSourceManager = eventSourceManager;
  }

  /**
   * @deprecated the EventMonitor to be used should now be retrieved from
   *             {@link Metrics#getEventMonitor()}
   * @param monitor to use
   */
  @Deprecated
  public static void setEventMonitor(EventMonitor monitor) {
    DefaultEventHandler.monitor = monitor;
  }

  /*
   * TODO: promote this interface to top-level, probably create a `monitoring` package?
   */
  public interface EventMonitor {
    EventMonitor NOOP = new EventMonitor() {
      @Override
      public void processedEvent(CustomResourceID uid, Event event) {}

      @Override
      public void failedEvent(CustomResourceID uid, Event event) {}
    };

    void processedEvent(CustomResourceID uid, Event event);

    void failedEvent(CustomResourceID uid, Event event);
  }

  private EventMonitor monitor() {
    // todo: remove us of static monitor, only here for backwards compatibility
    return DefaultEventHandler.monitor != EventMonitor.NOOP ? DefaultEventHandler.monitor
        : eventMonitor;
  }

  @Override
  public void handleEvent(Event event) {
    try {
      lock.lock();
      log.debug("Received event: {}", event);
      if (!this.running) {
        log.debug("Skipping event: {} because the event handler is shutting down", event);
        return;
      }
      final var monitor = monitor();
      eventBuffer.addEvent(event.getRelatedCustomResourceID(), event);
      monitor.processedEvent(event.getRelatedCustomResourceID(), event);
      executeBufferedEvents(event.getRelatedCustomResourceID());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    try {
      lock.lock();
      this.running = false;
    } finally {
      lock.unlock();
    }
  }

  private void executeBufferedEvents(CustomResourceID customResourceUid) {
    boolean newEventForResourceId = eventBuffer.containsEvents(customResourceUid);
    boolean controllerUnderExecution = isControllerUnderExecution(customResourceUid);
    Optional<R> latestCustomResource =
        resourceCache.getCustomResource(customResourceUid);

    if (!controllerUnderExecution && newEventForResourceId && latestCustomResource.isPresent()) {
      setUnderExecutionProcessing(customResourceUid);
      ExecutionScope executionScope =
          new ExecutionScope(
              eventBuffer.getAndRemoveEventsForExecution(customResourceUid),
              latestCustomResource.get(),
              retryInfo(customResourceUid));
      log.debug("Executing events for custom resource. Scope: {}", executionScope);
      executor.execute(new ControllerExecution(executionScope));
    } else {
      log.debug(
          "Skipping executing controller for resource id: {}. Events in queue: {}."
              + " Controller in execution: {}. Latest CustomResource present: {}",
          customResourceUid,
          newEventForResourceId,
          controllerUnderExecution,
          latestCustomResource.isPresent());
      if (latestCustomResource.isEmpty()) {
        log.warn("no custom resource found in cache for CustomResourceID: {}", customResourceUid);
      }
    }
  }

  private RetryInfo retryInfo(CustomResourceID customResourceUid) {
    return retryState.get(customResourceUid);
  }

  void eventProcessingFinished(
      ExecutionScope<R> executionScope, PostExecutionControl<R> postExecutionControl) {
    try {
      lock.lock();
      if (!running) {
        return;
      }

      log.debug(
          "Event processing finished. Scope: {}, PostExecutionControl: {}",
          executionScope,
          postExecutionControl);
      unsetUnderExecution(executionScope.getCustomResourceID());

      if (retry != null && postExecutionControl.exceptionDuringExecution()) {
        handleRetryOnException(executionScope);
        final var monitor = monitor();
        executionScope.getEvents()
            .forEach(e -> monitor.failedEvent(executionScope.getCustomResourceID(), e));
        return;
      }

      if (retry != null) {
        markSuccessfulExecutionRegardingRetry(executionScope);
      }
      if (containsCustomResourceDeletedEvent(executionScope.getEvents())) {
        cleanupAfterDeletedEvent(executionScope.getCustomResourceID());
      } else {
        reScheduleExecutionIfInstructed(postExecutionControl, executionScope.getCustomResource());
        executeBufferedEvents(executionScope.getCustomResourceID());
      }
    } finally {
      lock.unlock();
    }
  }

  private void reScheduleExecutionIfInstructed(PostExecutionControl<R> postExecutionControl,
      R customResource) {
    postExecutionControl.getReScheduleDelay().ifPresent(delay -> eventSourceManager
        .getRetryTimerEventSource()
        .scheduleOnce(customResource, delay));
  }

  /**
   * Regarding the events there are 2 approaches we can take. Either retry always when there are new
   * events (received meanwhile retry is in place or already in buffer) instantly or always wait
   * according to the retry timing if there was an exception.
   */
  private void handleRetryOnException(ExecutionScope<R> executionScope) {
    RetryExecution execution = getOrInitRetryExecution(executionScope);
    boolean newEventsExists = eventBuffer
        .newEventsExists(CustomResourceID.fromResource(executionScope.getCustomResource()));
    eventBuffer.putBackEvents(executionScope.getCustomResourceID(), executionScope.getEvents());

    if (newEventsExists) {
      log.debug("New events exists for for resource id: {}", executionScope.getCustomResourceID());
      executeBufferedEvents(executionScope.getCustomResourceID());
      return;
    }
    Optional<Long> nextDelay = execution.nextDelay();

    nextDelay.ifPresentOrElse(
        delay -> {
          log.debug(
              "Scheduling timer event for retry with delay:{} for resource: {}",
              delay,
              executionScope.getCustomResourceID());
          eventSourceManager
              .getRetryTimerEventSource()
              .scheduleOnce(executionScope.getCustomResource(), delay);
        },
        () -> log.error("Exhausted retries for {}", executionScope));
  }

  private void markSuccessfulExecutionRegardingRetry(ExecutionScope<R> executionScope) {
    log.debug(
        "Marking successful execution for resource: {}",
        getName(executionScope.getCustomResource()));
    retryState.remove(executionScope.getCustomResourceID());
    eventSourceManager
        .getRetryTimerEventSource()
        .cancelOnceSchedule(executionScope.getCustomResourceID());
  }

  private RetryExecution getOrInitRetryExecution(ExecutionScope<R> executionScope) {
    RetryExecution retryExecution = retryState.get(executionScope.getCustomResourceID());
    if (retryExecution == null) {
      retryExecution = retry.initExecution();
      retryState.put(executionScope.getCustomResourceID(), retryExecution);
    }
    return retryExecution;
  }

  private void cleanupAfterDeletedEvent(CustomResourceID customResourceUid) {
    eventSourceManager.cleanup(customResourceUid);
    eventBuffer.cleanup(customResourceUid);
  }

  private boolean isControllerUnderExecution(CustomResourceID customResourceUid) {
    return underProcessing.contains(customResourceUid);
  }

  private void setUnderExecutionProcessing(CustomResourceID customResourceUid) {
    underProcessing.add(customResourceUid);
  }

  private void unsetUnderExecution(CustomResourceID customResourceUid) {
    underProcessing.remove(customResourceUid);
  }


  private class ControllerExecution implements Runnable {
    private final ExecutionScope<R> executionScope;

    private ControllerExecution(ExecutionScope<R> executionScope) {
      this.executionScope = executionScope;
    }

    @Override
    public void run() {
      // change thread name for easier debugging
      Thread.currentThread().setName("EventHandler-" + controllerName);
      PostExecutionControl<R> postExecutionControl =
          eventDispatcher.handleExecution(executionScope);
      eventProcessingFinished(executionScope, postExecutionControl);
    }

    @Override
    public String toString() {
      return controllerName + " -> " + executionScope;
    }
  }
}
