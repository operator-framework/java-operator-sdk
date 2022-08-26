package io.javaoperatorsdk.operator.processing.event;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
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
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter.RateLimitState;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;

public class EventProcessor<R extends HasMetadata> implements EventHandler, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);
  private static final long MINIMAL_RATE_LIMIT_RESCHEDULE_DURATION = 50;

  private volatile boolean running;
  private final ReconciliationDispatcher<R> reconciliationDispatcher;
  private final Retry retry;
  private final ExecutorService executor;
  private final String controllerName;
  private final Metrics metrics;
  private final Cache<R> cache;
  private final EventSourceManager<R> eventSourceManager;
  private final RateLimiter<? extends RateLimitState> rateLimiter;
  private final ResourceStateManager resourceStateManager = new ResourceStateManager();
  private final Map<String, Object> metricsMetadata;

  public EventProcessor(EventSourceManager<R> eventSourceManager) {
    this(
        eventSourceManager.getControllerResourceEventSource(),
        ExecutorServiceManager.instance().executorService(),
        eventSourceManager.getController().getConfiguration().getName(),
        new ReconciliationDispatcher<>(eventSourceManager.getController()),
        eventSourceManager.getController().getConfiguration().getRetry(),
        ConfigurationServiceProvider.instance().getMetrics(),
        eventSourceManager.getController().getConfiguration().getRateLimiter(),
        eventSourceManager);
  }

  @SuppressWarnings("rawtypes")
  EventProcessor(
      ReconciliationDispatcher<R> reconciliationDispatcher,
      EventSourceManager<R> eventSourceManager,
      String relatedControllerName,
      Retry retry,
      RateLimiter rateLimiter,
      Metrics metrics) {
    this(
        eventSourceManager.getControllerResourceEventSource(),
        null,
        relatedControllerName,
        reconciliationDispatcher,
        retry,
        metrics,
        rateLimiter,
        eventSourceManager);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private EventProcessor(
      Cache<R> cache,
      ExecutorService executor,
      String relatedControllerName,
      ReconciliationDispatcher<R> reconciliationDispatcher,
      Retry retry,
      Metrics metrics,
      RateLimiter rateLimiter,
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
    this.rateLimiter = rateLimiter;

    metricsMetadata = Optional.ofNullable(eventSourceManager.getController())
        .map(Controller::getAssociatedGroupVersionKind)
        .map(gvk -> Map.of(Constants.RESOURCE_GVK_KEY, (Object) gvk))
        .orElse(Collections.emptyMap());
  }

  @Override
  public synchronized void handleEvent(Event event) {
    try {
      log.debug("Received event: {}", event);

      final var resourceID = event.getRelatedCustomResourceID();
      final var state = resourceStateManager.getOrCreate(event.getRelatedCustomResourceID());
      MDCUtils.addResourceIDInfo(resourceID);
      metrics.receivedEvent(event, metricsMetadata);
      handleEventMarking(event, state);
      if (!this.running) {
        // events are received and marked, but will be processed when started, see start() method.
        log.debug("Skipping event: {} because the event processor is not started", event);
        return;
      }
      handleMarkedEventForResource(state);
    } finally {
      MDCUtils.removeResourceIDInfo();
    }
  }

  private void handleMarkedEventForResource(ResourceState state) {
    if (state.deleteEventPresent()) {
      cleanupForDeletedEvent(state.getId());
    } else if (!state.processedMarkForDeletionPresent()) {
      submitReconciliationExecution(state);
    }
  }

  private void submitReconciliationExecution(ResourceState state) {
    try {
      boolean controllerUnderExecution = isControllerUnderExecution(state);
      Optional<R> maybeLatest = cache.get(state.getId());
      maybeLatest.ifPresent(MDCUtils::addResourceInfo);
      if (!controllerUnderExecution && maybeLatest.isPresent()) {
        var rateLimit = state.getRateLimit();
        if (rateLimit == null) {
          rateLimit = rateLimiter.initState();
          state.setRateLimit(rateLimit);
        }
        var rateLimiterPermission = rateLimiter.isLimited(rateLimit);
        if (rateLimiterPermission.isPresent()) {
          handleRateLimitedSubmission(state.getId(), rateLimiterPermission.get());
          return;
        }
        state.setUnderProcessing(true);
        final var latest = maybeLatest.get();
        ExecutionScope<R> executionScope = new ExecutionScope<>(latest, state.getRetry());
        state.unMarkEventReceived();
        metrics.reconcileCustomResource(state.getId(), state.getRetry(), metricsMetadata);
        log.debug("Executing events for custom resource. Scope: {}", executionScope);
        executor.execute(new ReconcilerExecutor(executionScope));
      } else {
        log.debug(
            "Skipping executing controller for resource id: {}. Controller in execution: {}. Latest Resource present: {}",
            state,
            controllerUnderExecution,
            maybeLatest.isPresent());
        if (maybeLatest.isEmpty()) {
          log.debug("no custom resource found in cache for ResourceID: {}", state);
        }
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  private void handleEventMarking(Event event, ResourceState state) {
    final var relatedCustomResourceID = event.getRelatedCustomResourceID();
    if (event instanceof ResourceEvent) {
      var resourceEvent = (ResourceEvent) event;
      if (resourceEvent.getAction() == ResourceAction.DELETED) {
        log.debug("Marking delete event received for: {}", relatedCustomResourceID);
        state.markDeleteEventReceived();
      } else {
        if (state.processedMarkForDeletionPresent() && isResourceMarkedForDeletion(resourceEvent)) {
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
        markEventReceived(state);
      }
    } else if (!state.deleteEventPresent() || !state.processedMarkForDeletionPresent()) {
      markEventReceived(state);
    } else if (log.isDebugEnabled()) {
      log.debug(
          "Skipped marking event as received. Delete event present: {}, processed mark for deletion: {}",
          state.deleteEventPresent(),
          state.processedMarkForDeletionPresent());
    }
  }

  private void markEventReceived(ResourceState state) {
    log.debug("Marking event received for: {}", state.getId());
    state.markEventReceived();
  }

  private boolean isResourceMarkedForDeletion(ResourceEvent resourceEvent) {
    return resourceEvent.getResource().map(HasMetadata::isMarkedForDeletion).orElse(false);
  }

  private void handleRateLimitedSubmission(ResourceID resourceID, Duration minimalDuration) {
    var minimalDurationMillis = minimalDuration.toMillis();
    log.debug("Rate limited resource: {}, rescheduled in {} millis", resourceID,
        minimalDurationMillis);
    retryEventSource().scheduleOnce(resourceID,
        Math.max(minimalDurationMillis, MINIMAL_RATE_LIMIT_RESCHEDULE_DURATION));
  }

  synchronized void eventProcessingFinished(
      ExecutionScope<R> executionScope, PostExecutionControl<R> postExecutionControl) {
    if (!running) {
      return;
    }
    ResourceID resourceID = executionScope.getResourceID();
    final var state = resourceStateManager.getOrCreate(resourceID);
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
        && !state.deleteEventPresent()) {
      handleRetryOnException(
          executionScope, postExecutionControl.getRuntimeException().orElseThrow());
      return;
    }
    cleanupOnSuccessfulExecution(executionScope);
    metrics.finishedReconciliation(resourceID, metricsMetadata);
    if (state.deleteEventPresent()) {
      cleanupForDeletedEvent(executionScope.getResourceID());
    } else if (postExecutionControl.isFinalizerRemoved()) {
      state.markProcessedMarkForDeletion();
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
      if (state.eventPresent()) {
        submitReconciliationExecution(state);
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
    final var state = getOrInitRetryExecution(executionScope);
    var resourceID = state.getId();
    boolean eventPresent = state.eventPresent();
    state.markEventReceived();

    if (eventPresent) {
      log.debug("New events exists for for resource id: {}", resourceID);
      submitReconciliationExecution(state);
      return;
    }
    Optional<Long> nextDelay = state.getRetry().nextDelay();

    nextDelay.ifPresentOrElse(
        delay -> {
          log.debug(
              "Scheduling timer event for retry with delay:{} for resource: {}",
              delay,
              resourceID);
          metrics.failedReconciliation(resourceID, exception, metricsMetadata);
          retryEventSource().scheduleOnce(resourceID, delay);
        },
        () -> log.error("Exhausted retries for {}", executionScope));
  }

  private void cleanupOnSuccessfulExecution(ExecutionScope<R> executionScope) {
    log.debug(
        "Cleanup for successful execution for resource: {}", getName(executionScope.getResource()));
    if (isRetryConfigured()) {
      resourceStateManager.getOrCreate(executionScope.getResourceID()).setRetry(null);
    }
    retryEventSource().cancelOnceSchedule(executionScope.getResourceID());
  }

  private ResourceState getOrInitRetryExecution(ExecutionScope<R> executionScope) {
    final var state = resourceStateManager.getOrCreate(executionScope.getResourceID());
    RetryExecution retryExecution = state.getRetry();
    if (retryExecution == null) {
      retryExecution = retry.initExecution();
      state.setRetry(retryExecution);
    }
    return state;
  }

  private void cleanupForDeletedEvent(ResourceID resourceID) {
    log.debug("Cleaning up for delete event for: {}", resourceID);
    resourceStateManager.remove(resourceID);
    metrics.cleanupDoneFor(resourceID, metricsMetadata);
  }

  private boolean isControllerUnderExecution(ResourceState state) {
    return state.isUnderProcessing();
  }

  private void unsetUnderExecution(ResourceID resourceID) {
    resourceStateManager.getOrCreate(resourceID).setUnderProcessing(false);
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
    for (var state : resourceStateManager.resourcesWithEventPresent()) {
      handleMarkedEventForResource(state);
    }
  }

  private class ReconcilerExecutor implements Runnable {
    private final ExecutionScope<R> executionScope;

    private ReconcilerExecutor(ExecutionScope<R> executionScope) {
      this.executionScope = executionScope;
    }

    @Override
    public void run() {
      // change thread name for easier debugging
      final var thread = Thread.currentThread();
      final var name = thread.getName();
      try {
        MDCUtils.addResourceInfo(executionScope.getResource());
        thread.setName("ReconcilerExecutor-" + controllerName + "-" + thread.getId());
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
    return isControllerUnderExecution(resourceStateManager.getOrCreate(resourceID));
  }
}
