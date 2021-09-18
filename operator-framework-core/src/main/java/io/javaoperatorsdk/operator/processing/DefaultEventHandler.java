package io.javaoperatorsdk.operator.processing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.RetryInfo;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

import static io.javaoperatorsdk.operator.EventListUtils.containsCustomResourceDeletedEvent;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Event handler that makes sure that events are processed in a "single threaded" way per resource
 * UID, while buffering events which are received during an execution.
 */
public class DefaultEventHandler<R extends CustomResource<?, ?>> implements EventHandler {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventHandler.class);
  private static EventMonitor monitor = new EventMonitor() {
    @Override
    public void processedEvent(String uid, Event event) {}

    @Override
    public void failedEvent(String uid, Event event) {}
  };

  private final EventBuffer eventBuffer;
  private final Set<String> underProcessing = new HashSet<>();
  private final EventDispatcher<R> eventDispatcher;
  private final Retry retry;
  private final Map<String, RetryExecution> retryState = new HashMap<>();
  private final ExecutorService executor;
  private final String controllerName;
  private final ReentrantLock lock = new ReentrantLock();
  private DefaultEventSourceManager<R> eventSourceManager;

  public DefaultEventHandler(ConfiguredController<R> controller) {
    this(controller.getConfiguration().getConfigurationService().getExecutorService(),
        controller.getConfiguration().getName(),
        new EventDispatcher<>(controller),
        GenericRetry.fromConfiguration(controller.getConfiguration().getRetryConfiguration()));
  }

  DefaultEventHandler(EventDispatcher<R> eventDispatcher, String relatedControllerName,
      Retry retry) {
    this(null, relatedControllerName, eventDispatcher, retry);
  }

  private DefaultEventHandler(ExecutorService executor, String relatedControllerName,
      EventDispatcher<R> eventDispatcher, Retry retry) {
    this.executor =
        executor == null
            ? new ScheduledThreadPoolExecutor(
                ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER)
            : executor;
    this.controllerName = relatedControllerName;
    this.eventDispatcher = eventDispatcher;
    this.retry = retry;
    eventBuffer = new EventBuffer();
  }

  public void setEventSourceManager(DefaultEventSourceManager<R> eventSourceManager) {
    this.eventSourceManager = eventSourceManager;
  }

  public static void setEventMonitor(EventMonitor monitor) {
    DefaultEventHandler.monitor = monitor;
  }

  public interface EventMonitor {
    void processedEvent(String uid, Event event);

    void failedEvent(String uid, Event event);
  }

  @Override
  public void handleEvent(Event event) {
    try {
      lock.lock();
      log.debug("Received event: {}", event);

      final Predicate<CustomResource> selector = event.getCustomResourcesSelector();
      for (String uid : eventSourceManager.getLatestResourceUids(selector)) {
        eventBuffer.addEvent(uid, event);
        monitor.processedEvent(uid, event);
        executeBufferedEvents(uid);
      }
    } finally {
      lock.unlock();
    }
  }

  private void executeBufferedEvents(String customResourceUid) {
    boolean newEventForResourceId = eventBuffer.containsEvents(customResourceUid);
    boolean controllerUnderExecution = isControllerUnderExecution(customResourceUid);
    Optional<CustomResource> latestCustomResource =
        eventSourceManager.getLatestResource(customResourceUid);

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
    }
  }

  private RetryInfo retryInfo(String customResourceUid) {
    return retryState.get(customResourceUid);
  }

  void eventProcessingFinished(
      ExecutionScope<R> executionScope, PostExecutionControl<R> postExecutionControl) {
    try {
      lock.lock();
      log.debug(
          "Event processing finished. Scope: {}, PostExecutionControl: {}",
          executionScope,
          postExecutionControl);
      unsetUnderExecution(executionScope.getCustomResourceUid());

      if (retry != null && postExecutionControl.exceptionDuringExecution()) {
        handleRetryOnException(executionScope);
        executionScope.getEvents()
            .forEach(e -> monitor.failedEvent(executionScope.getCustomResourceUid(), e));
        return;
      }

      if (retry != null) {
        markSuccessfulExecutionRegardingRetry(executionScope);
      }
      if (containsCustomResourceDeletedEvent(executionScope.getEvents())) {
        cleanupAfterDeletedEvent(executionScope.getCustomResourceUid());
      } else {
        cacheUpdatedResourceIfChanged(executionScope, postExecutionControl);
        executeBufferedEvents(executionScope.getCustomResourceUid());
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Regarding the events there are 2 approaches we can take. Either retry always when there are new
   * events (received meanwhile retry is in place or already in buffer) instantly or always wait
   * according to the retry timing if there was an exception.
   */
  private void handleRetryOnException(ExecutionScope<R> executionScope) {
    RetryExecution execution = getOrInitRetryExecution(executionScope);
    boolean newEventsExists = eventBuffer.newEventsExists(executionScope.getCustomResourceUid());
    eventBuffer.putBackEvents(executionScope.getCustomResourceUid(), executionScope.getEvents());

    if (newEventsExists) {
      log.debug("New events exists for for resource id: {}", executionScope.getCustomResourceUid());
      executeBufferedEvents(executionScope.getCustomResourceUid());
      return;
    }
    Optional<Long> nextDelay = execution.nextDelay();

    nextDelay.ifPresentOrElse(
        delay -> {
          log.debug(
              "Scheduling timer event for retry with delay:{} for resource: {}",
              delay,
              executionScope.getCustomResourceUid());
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
    retryState.remove(executionScope.getCustomResourceUid());
    eventSourceManager
        .getRetryTimerEventSource()
        .cancelOnceSchedule(executionScope.getCustomResourceUid());
  }

  private RetryExecution getOrInitRetryExecution(ExecutionScope<R> executionScope) {
    RetryExecution retryExecution = retryState.get(executionScope.getCustomResourceUid());
    if (retryExecution == null) {
      retryExecution = retry.initExecution();
      retryState.put(executionScope.getCustomResourceUid(), retryExecution);
    }
    return retryExecution;
  }

  /**
   * Here we try to cache the latest resource after an update. The goal is to solve a concurrency
   * issue we've seen: If an execution is finished, where we updated a custom resource, but there
   * are other events already buffered for next execution, we might not get the newest custom
   * resource from CustomResource event source in time. Thus we execute the next batch of events but
   * with a non up to date CR. Here we cache the latest CustomResource from the update execution so
   * we make sure its already used in the up-coming execution.
   *
   * <p>
   * Note that this is an improvement, not a bug fix. This situation can happen naturally, we just
   * make the execution more efficient, and avoid questions about conflicts.
   *
   * <p>
   * Note that without the conditional locking in the cache, there is a very minor chance that we
   * would override an additional change coming from a different client.
   */
  private void cacheUpdatedResourceIfChanged(
      ExecutionScope<R> executionScope, PostExecutionControl<R> postExecutionControl) {
    if (postExecutionControl.customResourceUpdatedDuringExecution()) {
      R originalCustomResource = executionScope.getCustomResource();
      R customResourceAfterExecution = postExecutionControl.getUpdatedCustomResource().get();
      String originalResourceVersion = getVersion(originalCustomResource);

      log.debug(
          "Trying to update resource cache from update response for resource: {} new version: {} old version: {}",
          getName(originalCustomResource),
          getVersion(customResourceAfterExecution),
          getVersion(originalCustomResource));
      eventSourceManager.cacheResource(
          customResourceAfterExecution,
          customResource -> getVersion(customResource).equals(originalResourceVersion)
              && !originalResourceVersion.equals(getVersion(customResourceAfterExecution)));
    }
  }

  private void cleanupAfterDeletedEvent(String customResourceUid) {
    eventSourceManager.cleanup(customResourceUid);
    eventBuffer.cleanup(customResourceUid);
  }

  private boolean isControllerUnderExecution(String customResourceUid) {
    return underProcessing.contains(customResourceUid);
  }

  private void setUnderExecutionProcessing(String customResourceUid) {
    underProcessing.add(customResourceUid);
  }

  private void unsetUnderExecution(String customResourceUid) {
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
