package io.javaoperatorsdk.operator.processing.event;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.expectation.DefaultExpectationContext;
import io.javaoperatorsdk.operator.api.reconciler.expectation.ExpectationContext;
import io.javaoperatorsdk.operator.api.reconciler.expectation.ExpectationResult;
import io.javaoperatorsdk.operator.api.reconciler.expectation.ExpectationStatus;
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

public class EventProcessor<P extends HasMetadata> implements EventHandler, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);
  private static final long MINIMAL_RATE_LIMIT_RESCHEDULE_DURATION = 50;

  private volatile boolean running;
  private final ControllerConfiguration<?> controllerConfiguration;
  private final ReconciliationDispatcher<P> reconciliationDispatcher;
  private final Retry retry;
  private final Metrics metrics;
  private final Cache<P> cache;
  private final EventSourceManager<P> eventSourceManager;
  private final RateLimiter<? extends RateLimitState> rateLimiter;
  private final ResourceStateManager resourceStateManager = new ResourceStateManager();
  private final Map<String, Object> metricsMetadata;
  private ExecutorService executor;

  public EventProcessor(
      EventSourceManager<P> eventSourceManager, ConfigurationService configurationService) {
    this(
        eventSourceManager.getController().getConfiguration(),
        new ReconciliationDispatcher<>(eventSourceManager.getController()),
        eventSourceManager,
        configurationService.getMetrics(),
        eventSourceManager.getControllerEventSource());
  }

  @SuppressWarnings("rawtypes")
  EventProcessor(
      ControllerConfiguration controllerConfiguration,
      ReconciliationDispatcher<P> reconciliationDispatcher,
      EventSourceManager<P> eventSourceManager,
      Metrics metrics) {
    this(
        controllerConfiguration,
        reconciliationDispatcher,
        eventSourceManager,
        metrics,
        eventSourceManager.getControllerEventSource());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private EventProcessor(
      ControllerConfiguration controllerConfiguration,
      ReconciliationDispatcher<P> reconciliationDispatcher,
      EventSourceManager<P> eventSourceManager,
      Metrics metrics,
      Cache<P> cache) {
    this.controllerConfiguration = controllerConfiguration;
    this.running = false;
    this.reconciliationDispatcher = reconciliationDispatcher;
    this.retry = controllerConfiguration.getRetry();
    this.cache = cache;
    this.metrics = metrics != null ? metrics : Metrics.NOOP;
    this.eventSourceManager = eventSourceManager;
    this.rateLimiter = controllerConfiguration.getRateLimiter();

    metricsMetadata =
        Optional.ofNullable(eventSourceManager.getController())
            .map(
                c ->
                    Map.of(
                        Constants.RESOURCE_GVK_KEY, c.getAssociatedGroupVersionKind(),
                        Constants.CONTROLLER_NAME, controllerConfiguration.getName()))
            .orElseGet(HashMap::new);
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
        if (state.deleteEventPresent()) {
          cleanupForDeletedEvent(state.getId());
        }
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

  @SuppressWarnings("rawtypes")
  private void submitReconciliationExecution(ResourceState state) {
    try {
      boolean controllerUnderExecution = isControllerUnderExecution(state);
      final var resourceID = state.getId();
      Optional<P> maybeLatest = cache.get(resourceID);
      maybeLatest.ifPresent(MDCUtils::addResourceInfo);
      if (!controllerUnderExecution && maybeLatest.isPresent()) {
        ExpectationResult expectationResult = null;
        if (isExpectationPresent(state)) {
          var expectationCheckResult =
              shouldProceedWithExpectation(state, maybeLatest.orElseThrow());
          if (expectationCheckResult.isEmpty()) {
            log.debug(
                "Skipping processing since expectation is not fulfilled. ResourceID: {}",
                resourceID);
            return;
          } else {
            expectationResult = expectationCheckResult.orElseThrow();
          }
        }

        var rateLimit = state.getRateLimit();
        if (rateLimit == null) {
          rateLimit = rateLimiter.initState();
          state.setRateLimit(rateLimit);
        }
        var rateLimiterPermission = rateLimiter.isLimited(rateLimit);
        if (rateLimiterPermission.isPresent()) {
          handleRateLimitedSubmission(resourceID, rateLimiterPermission.get());
          return;
        }
        state.setUnderProcessing(true);
        final var latest = maybeLatest.get();
        ExecutionScope<P> executionScope =
            new ExecutionScope<>(state.getRetry(), expectationResult);
        state.unMarkEventReceived();
        metrics.reconcileCustomResource(latest, state.getRetry(), metricsMetadata);
        log.debug("Executing events for custom resource. Scope: {}", executionScope);
        executor.execute(new ReconcilerExecutor(resourceID, executionScope));
      } else {
        log.debug(
            "Skipping executing controller for resource id: {}. Controller in execution: {}. Latest"
                + " Resource present: {}",
            resourceID,
            controllerUnderExecution,
            maybeLatest.isPresent());
        if (maybeLatest.isEmpty()) {
          // there can be multiple reasons why the primary resource is not present, one is that the
          // informer is currently disconnected from k8s api server, but will eventually receive the
          // resource. Other is that simply there is no primary resource present for an event, this
          // might indicate issue with the implementation, but could happen also naturally, thus
          // this is not necessarily a problem.
          log.debug("no primary resource found in cache with resource id: {}", resourceID);
        }
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  private boolean isExpectationPresent(ResourceState state) {
    return state.getExpectationHolder().isPresent();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  Optional<ExpectationResult> shouldProceedWithExpectation(ResourceState state, P primary) {

    var holder = state.getExpectationHolder().orElseThrow();
    if (holder.isTimedOut()) {
      return Optional.of(new ExpectationResult(ExpectationStatus.TIMEOUT, holder.getExpectation()));
    }
    ExpectationContext<P> expectationContext =
        new DefaultExpectationContext<>(this.eventSourceManager.getController(), primary);
    return holder.getExpectation().isFulfilled(primary, expectationContext)
        ? Optional.of(new ExpectationResult(ExpectationStatus.FULFILLED, holder.getExpectation()))
        : Optional.empty();
  }

  private void handleEventMarking(Event event, ResourceState state) {
    final var relatedCustomResourceID = event.getRelatedCustomResourceID();
    if (event instanceof ResourceEvent resourceEvent) {
      if (resourceEvent.getAction() == ResourceAction.DELETED) {
        log.debug("Marking delete event received for: {}", relatedCustomResourceID);
        state.markDeleteEventReceived();
      } else {
        if (state.processedMarkForDeletionPresent() && isResourceMarkedForDeletion(resourceEvent)) {
          log.debug(
              "Skipping mark of event received, since already processed mark for deletion and"
                  + " resource marked for deletion: {}",
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
    } else if (!state.deleteEventPresent() && !state.processedMarkForDeletionPresent()) {
      markEventReceived(state);
    } else if (log.isDebugEnabled()) {
      log.debug(
          "Skipped marking event as received. Delete event present: {}, processed mark for"
              + " deletion: {}",
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
    log.debug(
        "Rate limited resource: {}, rescheduled in {} millis", resourceID, minimalDurationMillis);
    retryEventSource()
        .scheduleOnce(
            resourceID, Math.max(minimalDurationMillis, MINIMAL_RATE_LIMIT_RESCHEDULE_DURATION));
  }

  synchronized void eventProcessingFinished(
      ExecutionScope<P> executionScope, PostExecutionControl<P> postExecutionControl) {
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

    logErrorIfNoRetryConfigured(executionScope, postExecutionControl);
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
    metrics.finishedReconciliation(executionScope.getResource(), metricsMetadata);
    if (state.deleteEventPresent()) {
      cleanupForDeletedEvent(executionScope.getResourceID());
    } else if (postExecutionControl.isFinalizerRemoved()) {
      state.markProcessedMarkForDeletion();
      metrics.cleanupDoneFor(resourceID, metricsMetadata);
    } else {
      // TODO what should be the relation between re-schedule and expectation
      // should we add a flag if trigger if expectation fails
      setExpectation(state, postExecutionControl);
      if (state.eventPresent()) {
        submitReconciliationExecution(state);
      } else {
        reScheduleExecutionIfInstructed(postExecutionControl, executionScope.getResource());
      }
    }
  }

  private void setExpectation(ResourceState state, PostExecutionControl<P> postExecutionControl) {
    postExecutionControl.getExpectation().ifPresent(state::setExpectation);
  }

  /**
   * In case retry is configured more complex error logging takes place, see handleRetryOnException
   */
  private void logErrorIfNoRetryConfigured(
      ExecutionScope<P> executionScope, PostExecutionControl<P> postExecutionControl) {
    if (!isRetryConfigured() && postExecutionControl.exceptionDuringExecution()) {
      log.error(
          "Error during event processing {}",
          executionScope,
          postExecutionControl.getRuntimeException().orElseThrow());
    }
  }

  private void reScheduleExecutionIfInstructed(
      PostExecutionControl<P> postExecutionControl, P customResource) {

    postExecutionControl
        .getReScheduleDelay()
        .ifPresentOrElse(
            delay -> {
              var resourceID = ResourceID.fromResource(customResource);
              log.debug("Rescheduling event for resource: {} with delay: {}", resourceID, delay);
              retryEventSource().scheduleOnce(resourceID, delay);
            },
            () -> scheduleExecutionForMaxReconciliationInterval(customResource));
  }

  private void scheduleExecutionForMaxReconciliationInterval(P customResource) {
    this.controllerConfiguration
        .maxReconciliationInterval()
        .ifPresent(
            m -> {
              var resourceID = ResourceID.fromResource(customResource);
              var delay = m.toMillis();
              log.debug(
                  "Rescheduling event for max reconciliation interval for resource: {} : "
                      + "with delay: {}",
                  resourceID,
                  delay);
              retryEventSource().scheduleOnce(resourceID, delay);
            });
  }

  TimerEventSource<P> retryEventSource() {
    return eventSourceManager.retryEventSource();
  }

  /**
   * Regarding the events there are 2 approaches we can take. Either retry always when there are new
   * events (received meanwhile retry is in place or already in buffer) instantly or always wait
   * according to the retry timing if there was an exception.
   */
  private void handleRetryOnException(ExecutionScope<P> executionScope, Exception exception) {
    final var state = getOrInitRetryExecution(executionScope);
    var resourceID = state.getId();
    boolean eventPresent = state.eventPresent();
    state.markEventReceived();

    retryAwareErrorLogging(state.getRetry(), eventPresent, exception, executionScope);
    if (eventPresent) {
      log.debug("New events exists for for resource id: {}", resourceID);
      submitReconciliationExecution(state);
      return;
    }
    Optional<Long> nextDelay = state.getRetry().nextDelay();

    nextDelay.ifPresentOrElse(
        delay -> {
          log.debug(
              "Scheduling timer event for retry with delay:{} for resource: {}", delay, resourceID);
          metrics.failedReconciliation(executionScope.getResource(), exception, metricsMetadata);
          retryEventSource().scheduleOnce(resourceID, delay);
        },
        () -> {
          log.error("Exhausted retries for scope {}.", executionScope);
          scheduleExecutionForMaxReconciliationInterval(executionScope.getResource());
        });
  }

  private void retryAwareErrorLogging(
      RetryExecution retry,
      boolean eventPresent,
      Exception exception,
      ExecutionScope<P> executionScope) {
    if (!retry.isLastAttempt()
        && exception instanceof KubernetesClientException ex
        && ex.getCode() == HttpURLConnection.HTTP_CONFLICT) {
      log.debug("Full client conflict error during event processing {}", executionScope, exception);
      log.info(
          "Resource Kubernetes Resource Creator/Update Conflict during reconciliation. Message:"
              + " {} Resource name: {}",
          ex.getMessage(),
          ex.getFullResourceName());
    } else if (eventPresent || !retry.isLastAttempt()) {
      log.warn(
          "Uncaught error during event processing {} - but another reconciliation will be attempted"
              + " because a superseding event has been received or another retry attempt is"
              + " pending.",
          executionScope,
          exception);
    } else {
      log.error(
          "Uncaught error during event processing {} - no superseding event is present and this is"
              + " the retry last attempt",
          executionScope,
          exception);
    }
  }

  private void cleanupOnSuccessfulExecution(ExecutionScope<P> executionScope) {
    log.debug(
        "Cleanup for successful execution for resource: {}", getName(executionScope.getResource()));
    if (isRetryConfigured()) {
      resourceStateManager.getOrCreate(executionScope.getResourceID()).setRetry(null);
    }
    retryEventSource().cancelOnceSchedule(executionScope.getResourceID());
  }

  private ResourceState getOrInitRetryExecution(ExecutionScope<P> executionScope) {
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
  public synchronized void start() throws OperatorException {
    log.debug("Starting event processor: {}", this);
    // on restart new executor service is created and needs to be set here
    executor =
        controllerConfiguration
            .getConfigurationService()
            .getExecutorServiceManager()
            .reconcileExecutorService();
    this.running = true;
    handleAlreadyMarkedEvents();
  }

  public boolean isNextReconciliationImminent(ResourceID resourceID) {
    return resourceStateManager.getOrCreate(resourceID).eventPresent();
  }

  private void handleAlreadyMarkedEvents() {
    for (var state : resourceStateManager.resourcesWithEventPresent()) {
      log.debug("Handling already marked event on start. State: {}", state);
      handleMarkedEventForResource(state);
    }
  }

  private class ReconcilerExecutor implements Runnable {
    private final ExecutionScope<P> executionScope;
    private final ResourceID resourceID;

    private ReconcilerExecutor(ResourceID resourceID, ExecutionScope<P> executionScope) {
      this.executionScope = executionScope;
      this.resourceID = resourceID;
    }

    @Override
    public void run() {
      if (!running) {
        // this is needed for the case when controller stopped, but there is a graceful shutdown
        // timeout. that should finish the currently executing reconciliations but not the ones
        // which where submitted but not started yet
        log.debug("Event processor not running skipping resource processing: {}", resourceID);
        return;
      }
      // change thread name for easier debugging
      final var thread = Thread.currentThread();
      final var name = thread.getName();
      try {
        var actualResource = cache.get(resourceID);
        if (actualResource.isEmpty()) {
          log.debug("Skipping execution; primary resource missing from cache: {}", resourceID);
          return;
        }
        actualResource.ifPresent(executionScope::setResource);
        MDCUtils.addResourceInfo(executionScope.getResource());
        metrics.reconciliationExecutionStarted(executionScope.getResource(), metricsMetadata);
        thread.setName("ReconcilerExecutor-" + controllerName() + "-" + thread.getId());
        PostExecutionControl<P> postExecutionControl =
            reconciliationDispatcher.handleExecution(executionScope);
        eventProcessingFinished(executionScope, postExecutionControl);
      } finally {
        metrics.reconciliationExecutionFinished(executionScope.getResource(), metricsMetadata);
        // restore original name
        thread.setName(name);
        MDCUtils.removeResourceInfo();
      }
    }

    @Override
    public String toString() {
      return controllerName()
          + " -> "
          + (executionScope.getResource() != null ? executionScope : resourceID);
    }
  }

  private String controllerName() {
    return controllerConfiguration.getName();
  }

  public synchronized boolean isUnderProcessing(ResourceID resourceID) {
    return isControllerUnderExecution(resourceStateManager.getOrCreate(resourceID));
  }

  public synchronized boolean isRunning() {
    return running;
  }
}
