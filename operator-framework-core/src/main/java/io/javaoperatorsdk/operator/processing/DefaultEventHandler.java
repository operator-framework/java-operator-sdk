package io.javaoperatorsdk.operator.processing;

import static io.javaoperatorsdk.operator.EventListUtils.containsCustomResourceDeletedEvent;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.RetryInfo;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

/**
 * Event handler that makes sure that events are processed in a "single threaded" way per resource
 * UID, while buffering events which are received during an execution.
 */
public class DefaultEventHandler<R extends CustomResource<?, ?>> implements EventHandler {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventHandler.class);

  private final EventBuffer eventBuffer;
  private final Set<String> underProcessing = new HashSet<>();
  private final ScheduledThreadPoolExecutor executor;
  private final EventDispatcher<R> eventDispatcher;
  private final Retry retry;
  private final Map<String, RetryExecution> retryState = new HashMap<>();
  private final String controllerName;
  private final int terminationTimeout;
  private final ReentrantLock lock = new ReentrantLock();
  private DefaultEventSourceManager<R> eventSourceManager;
  private final ControllerConfiguration<R> configuration;

  public DefaultEventHandler(ConfiguredController<R> controller) {
    this(
        new EventDispatcher<>(controller),
        controller.getConfiguration().getName(),
        GenericRetry.fromConfiguration(controller.getConfiguration().getRetryConfiguration()),
        controller.getConfiguration().getConfigurationService().concurrentReconciliationThreads(),
        controller.getConfiguration().getConfigurationService().getTerminationTimeoutSeconds(),
        controller.getConfiguration());
  }

  DefaultEventHandler(
      EventDispatcher<R> eventDispatcher,
      String relatedControllerName,
      Retry retry,
      int concurrentReconciliationThreads, ControllerConfiguration<R> configuration) {
    this(
        eventDispatcher,
        relatedControllerName,
        retry,
        concurrentReconciliationThreads,
        ConfigurationService.DEFAULT_TERMINATION_TIMEOUT_SECONDS, configuration);
  }

  private DefaultEventHandler(
      EventDispatcher<R> eventDispatcher,
      String relatedControllerName,
      Retry retry,
      int concurrentReconciliationThreads,
      int terminationTimeout, ControllerConfiguration<R> configuration) {
    this.eventDispatcher = eventDispatcher;
    this.retry = retry;
    this.controllerName = relatedControllerName;
    eventBuffer = new EventBuffer();
    this.terminationTimeout = terminationTimeout;
    executor =
        new ScheduledThreadPoolExecutor(
            concurrentReconciliationThreads,
            runnable -> new Thread(runnable, "EventHandler-" + relatedControllerName));
    this.configuration = configuration;
  }

  @Override
  public void close() {
    try {
      log.debug("Closing handler for {}", controllerName);
      executor.shutdown();
      if (!executor.awaitTermination(terminationTimeout, TimeUnit.SECONDS)) {
        executor.shutdownNow(); // if we timed out, waiting, cancel everything
      }
    } catch (InterruptedException e) {
      log.debug("Exception closing handler for {}: {}", controllerName, e.getLocalizedMessage());
    }
  }

  public void setEventSourceManager(DefaultEventSourceManager<R> eventSourceManager) {
    this.eventSourceManager = eventSourceManager;
  }

  @Override
  public void handleEvent(Event event) {
    try {
      lock.lock();
      log.debug("Received event: {}", event);

      final Predicate<CustomResource> selector = event.getCustomResourcesSelector();
      for (String uid : eventSourceManager.getLatestResourceUids(selector)) {
        eventBuffer.addEvent(uid, event);
        configuration
            .getConfigurationService()
            .getMetrics()
            .timeControllerEvents();
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
      executor.execute(new ExecutionConsumer(executionScope, eventDispatcher, this));
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
        configuration
            .getConfigurationService()
            .getMetrics()
            .timeControllerRetry();
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
}
