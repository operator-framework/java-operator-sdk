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
import io.javaoperatorsdk.operator.api.RetryInfo;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.monitoring.EventMonitor;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import io.javaoperatorsdk.operator.processing.event.internal.ResourceAction;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Event handler that makes sure that events are processed in a "single threaded" way per resource
 * UID, while buffering events which are received during an execution.
 */
public class DefaultEventHandler<R extends CustomResource<?, ?>> implements EventHandler {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventHandler.class);

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
  private final EventMarker eventMarker;

  public DefaultEventHandler(ConfiguredController<R> controller, ResourceCache<R> resourceCache) {
    this(
        resourceCache,
        ExecutorServiceManager.instance().executorService(),
        controller.getConfiguration().getName(),
        new EventDispatcher<>(controller),
        GenericRetry.fromConfiguration(controller.getConfiguration().getRetryConfiguration()),
        controller.getConfiguration().getConfigurationService().getMetrics().getEventMonitor(),
        new EventMarker());
  }

  DefaultEventHandler(EventDispatcher<R> eventDispatcher, ResourceCache<R> resourceCache,
      String relatedControllerName,
      Retry retry, EventMarker eventMarker) {
    this(resourceCache, null, relatedControllerName, eventDispatcher, retry, null, eventMarker);
  }

  private DefaultEventHandler(ResourceCache<R> resourceCache, ExecutorService executor,
      String relatedControllerName,
      EventDispatcher<R> eventDispatcher, Retry retry, EventMonitor monitor,
      EventMarker eventMarker) {
    this.running = true;
    this.executor =
        executor == null
            ? new ScheduledThreadPoolExecutor(
                ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER)
            : executor;
    this.controllerName = relatedControllerName;
    this.eventDispatcher = eventDispatcher;
    this.retry = retry;
    this.resourceCache = resourceCache;
    this.eventMonitor = monitor != null ? monitor : EventMonitor.NOOP;
    this.eventMarker = eventMarker;
  }

  public void setEventSourceManager(DefaultEventSourceManager<R> eventSourceManager) {
    this.eventSourceManager = eventSourceManager;
  }

  private EventMonitor monitor() {
    return eventMonitor;
  }

  @Override
  public void handleEvent(Event event) {
    lock.lock();
    try {
      log.debug("Received event: {}", event);
      if (!this.running) {
        log.debug("Skipping event: {} because the event handler is shutting down", event);
        return;
      }
      final var monitor = monitor();
      final var resourceID = event.getRelatedCustomResourceID();
      monitor.processedEvent(resourceID, event);

      handleEventMarking(event);
      if (!eventMarker.deleteEventPresent(resourceID)) {
        submitReconciliationExecution(resourceID);
      } else {
        cleanupForDeletedEvent(resourceID);
      }
    } finally {
      lock.unlock();
    }
  }

  private boolean submitReconciliationExecution(CustomResourceID customResourceUid) {
    boolean controllerUnderExecution = isControllerUnderExecution(customResourceUid);
    Optional<R> latestCustomResource =
        resourceCache.getCustomResource(customResourceUid);

    if (!controllerUnderExecution
        && latestCustomResource.isPresent()) {
      setUnderExecutionProcessing(customResourceUid);
      ExecutionScope<R> executionScope =
          new ExecutionScope<>(
              latestCustomResource.get(),
              retryInfo(customResourceUid));
      eventMarker.unMarkEventReceived(customResourceUid);
      log.debug("Executing events for custom resource. Scope: {}", executionScope);
      executor.execute(new ControllerExecution(executionScope));
      return true;
    } else {
      log.debug(
          "Skipping executing controller for resource id: {}."
              + " Controller in execution: {}. Latest CustomResource present: {}",
          customResourceUid,
          controllerUnderExecution,
          latestCustomResource.isPresent());
      if (latestCustomResource.isEmpty()) {
        log.warn("no custom resource found in cache for CustomResourceID: {}",
            customResourceUid);
      }
      return false;
    }
  }

  private void handleEventMarking(Event event) {
    if (event instanceof CustomResourceEvent &&
        ((CustomResourceEvent) event).getAction() == ResourceAction.DELETED) {
      eventMarker.markDeleteEventReceived(event);
    } else if (!eventMarker.deleteEventPresent(event.getRelatedCustomResourceID())) {
      eventMarker.markEventReceived(event);
    }
  }

  private RetryInfo retryInfo(CustomResourceID customResourceUid) {
    return retryState.get(customResourceUid);
  }

  void eventProcessingFinished(
      ExecutionScope<R> executionScope, PostExecutionControl<R> postExecutionControl) {
    lock.lock();
    try {
      if (!running) {
        return;
      }
      CustomResourceID customResourceID = executionScope.getCustomResourceID();
      log.debug(
          "Event processing finished. Scope: {}, PostExecutionControl: {}",
          executionScope,
          postExecutionControl);
      unsetUnderExecution(customResourceID);

      // If a delete event present at this phase, it was received during reconciliation.
      // So we either removed the finalizer during reconciliation or we don't use finalizers.
      // Either way we don't want to retry.
      if (isRetryConfigured() && postExecutionControl.exceptionDuringExecution() &&
          !eventMarker.deleteEventPresent(customResourceID)) {
        handleRetryOnException(executionScope);
        // todo revisit monitoring since events are not present anymore
        // final var monitor = monitor(); executionScope.getEvents().forEach(e ->
        // monitor.failedEvent(executionScope.getCustomResourceID(), e));
        return;
      }
      cleanupOnSuccessfulExecution(executionScope);
      if (eventMarker.deleteEventPresent(customResourceID)) {
        cleanupForDeletedEvent(executionScope.getCustomResourceID());
      } else {
        if (eventMarker.eventPresent(customResourceID)) {
          if (isCacheReadyForInstantReconciliation(executionScope, postExecutionControl)) {
            submitReconciliationExecution(customResourceID);
          } else {
            postponeReconciliationAndHandleCacheSyncEvent(customResourceID);
          }
        } else {
          reScheduleExecutionIfInstructed(postExecutionControl,
              executionScope.getCustomResource());
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void postponeReconciliationAndHandleCacheSyncEvent(CustomResourceID customResourceID) {
    eventSourceManager.getCustomResourceEventSource().whitelistNextEvent(customResourceID);
  }

  private boolean isCacheReadyForInstantReconciliation(ExecutionScope<R> executionScope,
      PostExecutionControl<R> postExecutionControl) {
    if (!postExecutionControl.customResourceUpdatedDuringExecution()) {
      return true;
    }
    String originalResourceVersion = getVersion(executionScope.getCustomResource());
    String customResourceVersionAfterExecution = getVersion(postExecutionControl
        .getUpdatedCustomResource()
        .orElseThrow(() -> new IllegalStateException(
            "Updated custom resource must be present at this point of time")));
    String cachedCustomResourceVersion = getVersion(resourceCache
        .getCustomResource(executionScope.getCustomResourceID())
        .orElseThrow(() -> new IllegalStateException(
            "Cached custom resource must be present at this point")));

    if (cachedCustomResourceVersion.equals(customResourceVersionAfterExecution)) {
      return true;
    }
    if (cachedCustomResourceVersion.equals(originalResourceVersion)) {
      return false;
    }
    // If the cached resource version equals neither the version before nor after execution
    // probably an update happened on the custom resource independent of the framework during
    // reconciliation. We cannot tell at this point if it happened before our update or before.
    // (Well we could if we would parse resource version, but that should not be done by definition)
    return true;
  }

  private void reScheduleExecutionIfInstructed(PostExecutionControl<R> postExecutionControl,
      R customResource) {
    postExecutionControl.getReScheduleDelay().ifPresent(delay -> eventSourceManager
        .getRetryAndRescheduleTimerEventSource()
        .scheduleOnce(customResource, delay));
  }

  /**
   * Regarding the events there are 2 approaches we can take. Either retry always when there are new
   * events (received meanwhile retry is in place or already in buffer) instantly or always wait
   * according to the retry timing if there was an exception.
   */
  private void handleRetryOnException(ExecutionScope<R> executionScope) {
    RetryExecution execution = getOrInitRetryExecution(executionScope);
    var customResourceID = executionScope.getCustomResourceID();
    boolean eventPresent = eventMarker.eventPresent(customResourceID);
    eventMarker.markEventReceived(customResourceID);

    if (eventPresent) {
      log.debug("New events exists for for resource id: {}",
          customResourceID);
      submitReconciliationExecution(customResourceID);
      return;
    }
    Optional<Long> nextDelay = execution.nextDelay();

    nextDelay.ifPresentOrElse(
        delay -> {
          log.debug(
              "Scheduling timer event for retry with delay:{} for resource: {}",
              delay,
              customResourceID);
          eventSourceManager
              .getRetryAndRescheduleTimerEventSource()
              .scheduleOnce(executionScope.getCustomResource(), delay);
        },
        () -> log.error("Exhausted retries for {}", executionScope));
  }

  private void cleanupOnSuccessfulExecution(ExecutionScope<R> executionScope) {
    log.debug(
        "Cleanup for successful execution for resource: {}",
        getName(executionScope.getCustomResource()));
    if (isRetryConfigured()) {
      retryState.remove(executionScope.getCustomResourceID());
    }
    eventSourceManager
        .getRetryAndRescheduleTimerEventSource()
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

  private void cleanupForDeletedEvent(CustomResourceID customResourceUid) {
    eventSourceManager.cleanupForCustomResource(customResourceUid);
    eventMarker.cleanup(customResourceUid);
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

  private boolean isRetryConfigured() {
    return retry != null;
  }

  @Override
  public void close() {
    lock.lock();
    try {
      this.running = false;
    } finally {
      lock.unlock();
    }
  }

  private class ControllerExecution implements Runnable {
    private final ExecutionScope<R> executionScope;

    private ControllerExecution(ExecutionScope<R> executionScope) {
      this.executionScope = executionScope;
    }

    @Override
    public void run() {
      // change thread name for easier debugging
      final var thread = Thread.currentThread();
      final var name = thread.getName();
      try {
        thread.setName("EventHandler-" + controllerName);
        PostExecutionControl<R> postExecutionControl =
            eventDispatcher.handleExecution(executionScope);
        eventProcessingFinished(executionScope, postExecutionControl);
      } finally {
        // restore original name
        thread.setName(name);
      }
    }

    @Override
    public String toString() {
      return controllerName + " -> " + executionScope;
    }
  }
}
