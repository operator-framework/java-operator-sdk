package io.javaoperatorsdk.operator.processing.event;

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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

class EventProcessor<R extends HasMetadata> implements EventHandler, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

  private final Set<ResourceID> underProcessing = new HashSet<>();
  private final ReconciliationDispatcher<R> reconciliationDispatcher;
  private final Retry retry;
  private final Map<ResourceID, RetryExecution> retryState = new HashMap<>();
  private final ExecutorService executor;
  private final String controllerName;
  private final ReentrantLock lock = new ReentrantLock();
  private final Metrics metrics;
  private volatile boolean running;
  private final ResourceCache<R> resourceCache;
  private final EventSourceManager<R> eventSourceManager;
  private final EventMarker eventMarker = new EventMarker();

  EventProcessor(EventSourceManager<R> eventSourceManager) {
    this(
        eventSourceManager.getControllerResourceEventSource().getResourceCache(),
        ExecutorServiceManager.instance().executorService(),
        eventSourceManager.getController().getConfiguration().getName(),
        new ReconciliationDispatcher<>(eventSourceManager.getController()),
        GenericRetry.fromConfiguration(
            eventSourceManager.getController().getConfiguration().getRetryConfiguration()),
        eventSourceManager.getController().getConfiguration().getConfigurationService()
            .getMetrics(),
        eventSourceManager);
  }

  EventProcessor(ReconciliationDispatcher<R> reconciliationDispatcher,
      EventSourceManager<R> eventSourceManager,
      String relatedControllerName,
      Retry retry) {
    this(eventSourceManager.getControllerResourceEventSource().getResourceCache(), null,
        relatedControllerName,
        reconciliationDispatcher, retry, null, eventSourceManager);
  }

  private EventProcessor(ResourceCache<R> resourceCache, ExecutorService executor,
      String relatedControllerName,
      ReconciliationDispatcher<R> reconciliationDispatcher, Retry retry, Metrics metrics,
      EventSourceManager<R> eventSourceManager) {
    this.running = true;
    this.executor =
        executor == null
            ? new ScheduledThreadPoolExecutor(
                ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER)
            : executor;
    this.controllerName = relatedControllerName;
    this.reconciliationDispatcher = reconciliationDispatcher;
    this.retry = retry;
    this.resourceCache = resourceCache;
    this.metrics = metrics != null ? metrics : Metrics.NOOP;
    this.eventSourceManager = eventSourceManager;
  }

  EventMarker getEventMarker() {
    return eventMarker;
  }

  @Override
  public void handleEvent(Event event) {
    lock.lock();
    try {
      log.debug("Received event: {}", event);
      if (!this.running) {
        log.debug("Skipping event: {} because the event handler is not started", event);
        return;
      }
      final var resourceID = event.getRelatedCustomResourceID();
      MDCUtils.addResourceIDInfo(resourceID);
      metrics.receivedEvent(event);

      handleEventMarking(event);
      if (!eventMarker.deleteEventPresent(resourceID)) {
        submitReconciliationExecution(resourceID);
      } else {
        cleanupForDeletedEvent(resourceID);
      }
    } finally {
      lock.unlock();
      MDCUtils.removeResourceIDInfo();
    }
  }

  private void submitReconciliationExecution(ResourceID resourceID) {
    try {
      boolean controllerUnderExecution = isControllerUnderExecution(resourceID);
      Optional<R> latest = resourceCache.get(resourceID);
      latest.ifPresent(MDCUtils::addResourceInfo);
      if (!controllerUnderExecution && latest.isPresent()) {
        setUnderExecutionProcessing(resourceID);
        final var retryInfo = retryInfo(resourceID);
        ExecutionScope<R> executionScope = new ExecutionScope<>(latest.get(), retryInfo);
        eventMarker.unMarkEventReceived(resourceID);
        metrics.reconcileCustomResource(resourceID, retryInfo);
        log.debug("Executing events for custom resource. Scope: {}", executionScope);
        executor.execute(new ControllerExecution(executionScope));
      } else {
        log.debug(
            "Skipping executing controller for resource id: {}. Controller in execution: {}. Latest Resource present: {}",
            resourceID,
            controllerUnderExecution,
            latest.isPresent());
        if (latest.isEmpty()) {
          log.warn("no custom resource found in cache for ResourceID: {}", resourceID);
        }
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  private void handleEventMarking(Event event) {
    if (event instanceof ResourceEvent &&
        ((ResourceEvent) event).getAction() == ResourceAction.DELETED) {
      eventMarker.markDeleteEventReceived(event);
    } else if (!eventMarker.deleteEventPresent(event.getRelatedCustomResourceID())) {
      eventMarker.markEventReceived(event);
    }
  }

  private RetryInfo retryInfo(ResourceID customResourceUid) {
    return retryState.get(customResourceUid);
  }

  void eventProcessingFinished(
      ExecutionScope<R> executionScope, PostExecutionControl<R> postExecutionControl) {
    lock.lock();
    try {
      if (!running) {
        return;
      }
      ResourceID resourceID = executionScope.getCustomResourceID();
      log.debug(
          "Event processing finished. Scope: {}, PostExecutionControl: {}",
          executionScope,
          postExecutionControl);
      unsetUnderExecution(resourceID);

      // If a delete event present at this phase, it was received during reconciliation.
      // So we either removed the finalizer during reconciliation or we don't use finalizers.
      // Either way we don't want to retry.
      if (isRetryConfigured() && postExecutionControl.exceptionDuringExecution() &&
          !eventMarker.deleteEventPresent(resourceID)) {
        handleRetryOnException(executionScope,
            postExecutionControl.getRuntimeException().orElseThrow());
        return;
      }
      cleanupOnSuccessfulExecution(executionScope);
      metrics.finishedReconciliation(resourceID);
      if (eventMarker.deleteEventPresent(resourceID)) {
        cleanupForDeletedEvent(executionScope.getCustomResourceID());
      } else {
        if (eventMarker.eventPresent(resourceID)) {
          if (isCacheReadyForInstantReconciliation(executionScope, postExecutionControl)) {
            submitReconciliationExecution(resourceID);
          } else {
            postponeReconciliationAndHandleCacheSyncEvent(resourceID);
          }
        } else {
          reScheduleExecutionIfInstructed(postExecutionControl,
              executionScope.getResource());
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void postponeReconciliationAndHandleCacheSyncEvent(ResourceID resourceID) {
    eventSourceManager.getControllerResourceEventSource().whitelistNextEvent(resourceID);
  }

  private boolean isCacheReadyForInstantReconciliation(ExecutionScope<R> executionScope,
      PostExecutionControl<R> postExecutionControl) {
    if (!postExecutionControl.customResourceUpdatedDuringExecution()) {
      return true;
    }
    String originalResourceVersion = getVersion(executionScope.getResource());
    String customResourceVersionAfterExecution = getVersion(postExecutionControl
        .getUpdatedCustomResource()
        .orElseThrow(() -> new IllegalStateException(
            "Updated custom resource must be present at this point of time")));
    String cachedCustomResourceVersion = getVersion(resourceCache
        .get(executionScope.getCustomResourceID())
        .orElseThrow(() -> new IllegalStateException(
            "Cached custom resource must be present at this point")));

    if (cachedCustomResourceVersion.equals(customResourceVersionAfterExecution)) {
      return true;
    }
    // If the cached resource version equals neither the version before nor after execution
    // probably an update happened on the custom resource independent of the framework during
    // reconciliation. We cannot tell at this point if it happened before our update or before.
    // (Well we could if we would parse resource version, but that should not be done by definition)
    return !cachedCustomResourceVersion.equals(originalResourceVersion);
  }

  private void reScheduleExecutionIfInstructed(PostExecutionControl<R> postExecutionControl,
      R customResource) {
    postExecutionControl.getReScheduleDelay()
        .ifPresent(delay -> retryEventSource().scheduleOnce(customResource, delay));
  }

  TimerEventSource<R> retryEventSource() {
    return eventSourceManager.retryEventSource();
  }

  /**
   * Regarding the events there are 2 approaches we can take. Either retry always when there are new
   * events (received meanwhile retry is in place or already in buffer) instantly or always wait
   * according to the retry timing if there was an exception.
   */
  private void handleRetryOnException(ExecutionScope<R> executionScope,
      RuntimeException exception) {
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
          metrics.failedReconciliation(customResourceID, exception);
          retryEventSource().scheduleOnce(executionScope.getResource(), delay);
        },
        () -> log.error("Exhausted retries for {}", executionScope));
  }

  private void cleanupOnSuccessfulExecution(ExecutionScope<R> executionScope) {
    log.debug(
        "Cleanup for successful execution for resource: {}",
        getName(executionScope.getResource()));
    if (isRetryConfigured()) {
      retryState.remove(executionScope.getCustomResourceID());
    }
    retryEventSource().cancelOnceSchedule(executionScope.getCustomResourceID());
  }

  private RetryExecution getOrInitRetryExecution(ExecutionScope<R> executionScope) {
    RetryExecution retryExecution = retryState.get(executionScope.getCustomResourceID());
    if (retryExecution == null) {
      retryExecution = retry.initExecution();
      retryState.put(executionScope.getCustomResourceID(), retryExecution);
    }
    return retryExecution;
  }

  private void cleanupForDeletedEvent(ResourceID customResourceUid) {
    eventMarker.cleanup(customResourceUid);
    metrics.cleanupDoneFor(customResourceUid);
  }

  private boolean isControllerUnderExecution(ResourceID customResourceUid) {
    return underProcessing.contains(customResourceUid);
  }

  private void setUnderExecutionProcessing(ResourceID customResourceUid) {
    underProcessing.add(customResourceUid);
  }

  private void unsetUnderExecution(ResourceID customResourceUid) {
    underProcessing.remove(customResourceUid);
  }

  private boolean isRetryConfigured() {
    return retry != null;
  }

  @Override
  public void stop() {
    lock.lock();
    try {
      this.running = false;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void start() throws OperatorException {
    lock.lock();
    try {
      this.running = true;
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
        MDCUtils.addResourceInfo(executionScope.getResource());
        thread.setName("EventHandler-" + controllerName);
        PostExecutionControl<R> postExecutionControl =
            reconciliationDispatcher.handleExecution(executionScope);
        eventProcessingFinished(executionScope, postExecutionControl);
      } finally {
        // restore original name
        thread.setName(name);
        MDCUtils.removeResourceInfo();
      }
    }

    @Override
    public String toString() {
      return controllerName + " -> " + executionScope;
    }
  }
}
