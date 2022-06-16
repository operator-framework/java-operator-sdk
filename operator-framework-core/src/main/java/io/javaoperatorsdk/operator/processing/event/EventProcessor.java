package io.javaoperatorsdk.operator.processing.event;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;

class EventProcessor<R extends HasMetadata> implements EventHandler, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

  private final Set<ResourceID> underProcessing = new HashSet<>();
  private final ReconciliationDispatcher<R> reconciliationDispatcher;
  private final Retry retry;
  private final Map<ResourceID, RetryExecution> retryState = new HashMap<>();
  private final ExecutorService executor;
  private final String controllerName;
  private final Metrics metrics;
  private volatile boolean running;
  private final Cache<R> cache;
  private final EventSourceManager<R> eventSourceManager;
  private final EventMarker eventMarker = new EventMarker();
  private final RateLimiter rateLimiter;

  EventProcessor(EventSourceManager<R> eventSourceManager) {
    this(
        eventSourceManager.getControllerResourceEventSource(),
        ExecutorServiceManager.instance().executorService(),
        eventSourceManager.getController().getConfiguration().getName(),
        new ReconciliationDispatcher<>(eventSourceManager.getController()),
        eventSourceManager.getController().getConfiguration().getRetry(),
        ConfigurationServiceProvider.instance().getMetrics(),
        eventSourceManager);
  }

  EventProcessor(
      ReconciliationDispatcher<R> reconciliationDispatcher,
      EventSourceManager<R> eventSourceManager,
      String relatedControllerName,
      Retry retry,
      Metrics metrics) {
    this(
        eventSourceManager.getControllerResourceEventSource(),
        null,
        relatedControllerName,
        reconciliationDispatcher,
        retry,
        metrics,
        eventSourceManager);
  }

  private EventProcessor(
      Cache<R> cache,
      ExecutorService executor,
      String relatedControllerName,
      ReconciliationDispatcher<R> reconciliationDispatcher,
      Retry retry,
      Metrics metrics,
      EventSourceManager<R> eventSourceManager) {
    this.running = false;
    this.executor =
        executor == null
            ? new ScheduledThreadPoolExecutor(
                ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER)
            : executor;
    this.controllerName = relatedControllerName;
    this.reconciliationDispatcher = reconciliationDispatcher;
    this.retry = retry;
    this.cache = cache;
    this.metrics = metrics != null ? metrics : Metrics.NOOP;
    this.eventSourceManager = eventSourceManager;
    // todo configure
    this.rateLimiter = new RateLimiter(Duration.ofSeconds(1), 5);
  }

  @Override
  public synchronized void handleEvent(Event event) {
    try {
      log.debug("Received event: {}", event);

      final var resourceID = event.getRelatedCustomResourceID();
      MDCUtils.addResourceIDInfo(resourceID);
      metrics.receivedEvent(event);
      handleEventMarking(event);
      if (!this.running) {
        // events are received and marked, but will be processed when started, see start() method.
        log.debug("Skipping event: {} because the event processor is not started", event);
        return;
      }
      handleMarkedEventForResource(resourceID);
    } finally {
      MDCUtils.removeResourceIDInfo();
    }
  }

  private void handleMarkedEventForResource(ResourceID resourceID) {
    if (eventMarker.deleteEventPresent(resourceID)) {
      cleanupForDeletedEvent(resourceID);
    } else if (!eventMarker.processedMarkForDeletionPresent(resourceID)) {
      submitReconciliationExecution(resourceID);
    }
  }

  private void submitReconciliationExecution(ResourceID resourceID) {
    try {
      boolean controllerUnderExecution = isControllerUnderExecution(resourceID);
      Optional<R> latest = cache.get(resourceID);
      latest.ifPresent(MDCUtils::addResourceInfo);
      if (!controllerUnderExecution && latest.isPresent()) {
        var rateLimiterPermission = rateLimiter.acquirePermission(resourceID);
        if (rateLimiterPermission.isPresent()) {
          handleRateLimitedSubmission(resourceID, rateLimiterPermission.get());
          return;
        }
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
          log.debug("no custom resource found in cache for ResourceID: {}", resourceID);
        }
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  private void handleEventMarking(Event event) {
    final var relatedCustomResourceID = event.getRelatedCustomResourceID();
    if (event instanceof ResourceEvent) {
      var resourceEvent = (ResourceEvent) event;
      if (resourceEvent.getAction() == ResourceAction.DELETED) {
        log.debug("Marking delete event received for: {}", relatedCustomResourceID);
        eventMarker.markDeleteEventReceived(event);
      } else {
        if (eventMarker.processedMarkForDeletionPresent(relatedCustomResourceID)
            && isResourceMarkedForDeletion(resourceEvent)) {
          log.debug(
              "Skipping mark of event received, since already processed mark for deletion and resource marked for deletion: {}",
              relatedCustomResourceID);
          return;
        }
        // Normally when eventMarker is in state PROCESSED_MARK_FOR_DELETION it is expected to
        // receive a Delete event or an event where resource is marked for deletion. In a rare edge
        // case however it can happen that the finalizer related to the current controller is
        // removed, but also the informers websocket is disconnected and later reconnected. So
        // meanwhile the resource could be deleted and recreated. In this case we just mark a new
        // event as below.
        markEventReceived(event);
      }
    } else if (!eventMarker.deleteEventPresent(relatedCustomResourceID) ||
        !eventMarker.processedMarkForDeletionPresent(relatedCustomResourceID)) {
      markEventReceived(event);
    } else if (log.isDebugEnabled()) {
      log.debug(
          "Skipped marking event as received. Delete event present: {}, processed mark for deletion: {}",
          eventMarker.deleteEventPresent(relatedCustomResourceID),
          eventMarker.processedMarkForDeletionPresent(relatedCustomResourceID));
    }
  }

  private void markEventReceived(Event event) {
    log.debug("Marking event received for: {}", event.getRelatedCustomResourceID());
    eventMarker.markEventReceived(event);
  }

  private boolean isResourceMarkedForDeletion(ResourceEvent resourceEvent) {
    return resourceEvent.getResource().map(HasMetadata::isMarkedForDeletion).orElse(false);
  }

  private void handleRateLimitedSubmission(ResourceID resourceID, Duration minimalDuration) {
    var minimalDurationMillis = minimalDuration.toMillis();
    log.debug("Rate limited resource: {}, rescheduled in {} millis", resourceID,
        minimalDurationMillis);
    retryEventSource().scheduleOnce(resourceID, minimalDurationMillis);
  }

  private RetryInfo retryInfo(ResourceID resourceID) {
    return retryState.get(resourceID);
  }

  synchronized void eventProcessingFinished(
      ExecutionScope<R> executionScope, PostExecutionControl<R> postExecutionControl) {
    if (!running) {
      return;
    }
    ResourceID resourceID = executionScope.getResourceID();
    log.debug(
        "Event processing finished. Scope: {}, PostExecutionControl: {}",
        executionScope,
        postExecutionControl);
    unsetUnderExecution(resourceID);

    // If a delete event present at this phase, it was received during reconciliation.
    // So we either removed the finalizer during reconciliation or we don't use finalizers.
    // Either way we don't want to retry.
    if (isRetryConfigured()
        && postExecutionControl.exceptionDuringExecution()
        && !eventMarker.deleteEventPresent(resourceID)) {
      handleRetryOnException(
          executionScope, postExecutionControl.getRuntimeException().orElseThrow());
      return;
    }
    cleanupOnSuccessfulExecution(executionScope);
    metrics.finishedReconciliation(resourceID);
    if (eventMarker.deleteEventPresent(resourceID)) {
      cleanupForDeletedEvent(executionScope.getResourceID());
    } else if (postExecutionControl.isFinalizerRemoved()) {
      eventMarker.markProcessedMarkForDeletion(resourceID);
    } else {
      postExecutionControl
          .getUpdatedCustomResource()
          .ifPresent(
              r -> {
                if (!postExecutionControl.updateIsStatusPatch()) {
                  eventSourceManager
                      .getControllerResourceEventSource()
                      .handleRecentResourceUpdate(
                          ResourceID.fromResource(r), r, executionScope.getResource());
                }
              });
      if (eventMarker.eventPresent(resourceID)) {
        submitReconciliationExecution(resourceID);
      } else {
        reScheduleExecutionIfInstructed(postExecutionControl, executionScope.getResource());
      }
    }

  }

  private void reScheduleExecutionIfInstructed(
      PostExecutionControl<R> postExecutionControl, R customResource) {
    postExecutionControl
        .getReScheduleDelay()
        .ifPresent(delay -> {
          var resourceID = ResourceID.fromResource(customResource);
          log.debug("ReScheduling event for resource: {} with delay: {}",
              resourceID, delay);
          retryEventSource().scheduleOnce(resourceID, delay);
        });
  }

  TimerEventSource<R> retryEventSource() {
    return eventSourceManager.retryEventSource();
  }

  /**
   * Regarding the events there are 2 approaches we can take. Either retry always when there are new
   * events (received meanwhile retry is in place or already in buffer) instantly or always wait
   * according to the retry timing if there was an exception.
   */
  private void handleRetryOnException(
      ExecutionScope<R> executionScope, Exception exception) {
    RetryExecution execution = getOrInitRetryExecution(executionScope);
    var resourceID = executionScope.getResourceID();
    boolean eventPresent = eventMarker.eventPresent(resourceID);
    eventMarker.markEventReceived(resourceID);

    if (eventPresent) {
      log.debug("New events exists for for resource id: {}", resourceID);
      submitReconciliationExecution(resourceID);
      return;
    }
    Optional<Long> nextDelay = execution.nextDelay();

    nextDelay.ifPresentOrElse(
        delay -> {
          log.debug(
              "Scheduling timer event for retry with delay:{} for resource: {}",
              delay,
              resourceID);
          metrics.failedReconciliation(resourceID, exception);
          retryEventSource().scheduleOnce(resourceID, delay);
        },
        () -> log.error("Exhausted retries for {}", executionScope));
  }

  private void cleanupOnSuccessfulExecution(ExecutionScope<R> executionScope) {
    log.debug(
        "Cleanup for successful execution for resource: {}", getName(executionScope.getResource()));
    if (isRetryConfigured()) {
      retryState.remove(executionScope.getResourceID());
    }
    retryEventSource().cancelOnceSchedule(executionScope.getResourceID());
  }

  private RetryExecution getOrInitRetryExecution(ExecutionScope<R> executionScope) {
    RetryExecution retryExecution = retryState.get(executionScope.getResourceID());
    if (retryExecution == null) {
      retryExecution = retry.initExecution();
      retryState.put(executionScope.getResourceID(), retryExecution);
    }
    return retryExecution;
  }

  private void cleanupForDeletedEvent(ResourceID resourceID) {
    log.debug("Cleaning up for delete event for: {}", resourceID);
    eventMarker.cleanup(resourceID);
    metrics.cleanupDoneFor(resourceID);
  }

  private boolean isControllerUnderExecution(ResourceID resourceID) {
    return underProcessing.contains(resourceID);
  }

  private void setUnderExecutionProcessing(ResourceID resourceID) {
    underProcessing.add(resourceID);
  }

  private void unsetUnderExecution(ResourceID resourceID) {
    underProcessing.remove(resourceID);
  }

  private boolean isRetryConfigured() {
    return retry != null;
  }

  @Override
  public synchronized void stop() {
    this.running = false;
  }

  @Override
  public void start() throws OperatorException {
    this.running = true;
    handleAlreadyMarkedEvents();
  }

  private void handleAlreadyMarkedEvents() {
    for (ResourceID resourceID : eventMarker.resourceIDsWithEventPresent()) {
      handleMarkedEventForResource(resourceID);
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

  public synchronized boolean isUnderProcessing(ResourceID resourceID) {
    return underProcessing.contains(resourceID);
  }
}
