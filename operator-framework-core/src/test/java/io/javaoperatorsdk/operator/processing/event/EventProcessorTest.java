package io.javaoperatorsdk.operator.processing.event;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter.RateLimitState;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.TestUtils.markForDeletion;
import static io.javaoperatorsdk.operator.TestUtils.testCustomResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class EventProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(EventProcessorTest.class);

  public static final int FAKE_CONTROLLER_EXECUTION_DURATION = 250;
  public static final int SEPARATE_EXECUTION_TIMEOUT = 450;
  public static final String TEST_NAMESPACE = "default-event-handler-test";
  public static final int TIME_TO_WAIT_AFTER_SUBMISSION_BEFORE_EXECUTION = 150;
  public static final int DISPATCHING_DELAY = 250;

  private final ReconciliationDispatcher reconciliationDispatcherMock =
      mock(ReconciliationDispatcher.class);
  private final EventSourceManager eventSourceManagerMock = mock(EventSourceManager.class);
  private final TimerEventSource retryTimerEventSourceMock = mock(TimerEventSource.class);
  private final ControllerResourceEventSource controllerResourceEventSourceMock =
      mock(ControllerResourceEventSource.class);
  private final Metrics metricsMock = mock(Metrics.class);
  private EventProcessor eventProcessor;
  private EventProcessor eventProcessorWithRetry;
  private final RateLimiter rateLimiterMock = mock(RateLimiter.class);

  @BeforeEach
  void setup() {
    when(eventSourceManagerMock.getControllerResourceEventSource())
        .thenReturn(controllerResourceEventSourceMock);
    eventProcessor =
        spy(new EventProcessor(controllerConfiguration(null, rateLimiterMock),
            reconciliationDispatcherMock,
            eventSourceManagerMock, null));
    eventProcessor.start();
    eventProcessorWithRetry =
        spy(new EventProcessor(
            controllerConfiguration(GenericRetry.defaultLimitedExponentialRetry(),
                rateLimiterMock),
            reconciliationDispatcherMock, eventSourceManagerMock, null));
    eventProcessorWithRetry.start();
    when(eventProcessor.retryEventSource()).thenReturn(retryTimerEventSourceMock);
    when(eventProcessorWithRetry.retryEventSource()).thenReturn(retryTimerEventSourceMock);
    when(rateLimiterMock.isLimited(any())).thenReturn(Optional.empty());
  }

  @Test
  void dispatchesEventsIfNoExecutionInProgress() {
    eventProcessor.handleEvent(prepareCREvent());

    verify(reconciliationDispatcherMock, timeout(50).times(1)).handleExecution(any());
  }

  @Test
  void skipProcessingIfLatestCustomResourceNotInCache() {
    Event event = prepareCREvent();
    when(controllerResourceEventSourceMock.get(event.getRelatedCustomResourceID()))
        .thenReturn(Optional.empty());

    eventProcessor.handleEvent(event);

    verify(reconciliationDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  @Test
  void ifExecutionInProgressWaitsUntilItsFinished() {
    ResourceID resourceUid = eventAlreadyUnderProcessing();

    eventProcessor.handleEvent(nonCREvent(resourceUid));

    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .handleExecution(any());
  }

  @Test
  void schedulesAnEventRetryOnException() {
    TestCustomResource customResource = testCustomResource();

    ExecutionScope executionScope =
        new ExecutionScope(null);
    executionScope.setResource(customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(retryTimerEventSourceMock, times(1))
        .scheduleOnce(eq(ResourceID.fromResource(customResource)),
            eq(RetryConfiguration.DEFAULT_INITIAL_INTERVAL));
  }

  @Test
  void executesTheControllerInstantlyAfterErrorIfNewEventsReceived() {
    Event event = prepareCREvent();
    TestCustomResource customResource = testCustomResource();
    overrideData(event.getRelatedCustomResourceID(), customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));

    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenAnswer((Answer<PostExecutionControl>) invocationOnMock -> {
          // avoid to process the first event before the second submitted
          Thread.sleep(50);
          return postExecutionControl;
        })
        .thenReturn(PostExecutionControl.defaultDispatch());

    // start processing an event
    eventProcessorWithRetry.handleEvent(event);
    // handle another event
    eventProcessorWithRetry.handleEvent(event);

    ArgumentCaptor<ExecutionScope> executionScopeArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionScope.class);
    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(executionScopeArgumentCaptor.capture());
    List<ExecutionScope> allValues = executionScopeArgumentCaptor.getAllValues();
    assertThat(allValues).hasSize(2);
    verify(retryTimerEventSourceMock, never())
        .scheduleOnce(eq(ResourceID.fromResource(customResource)),
            eq(RetryConfiguration.DEFAULT_INITIAL_INTERVAL));
  }

  @Test
  void successfulExecutionResetsTheRetry() {
    log.info("Starting successfulExecutionResetsTheRetry");

    Event event = prepareCREvent();
    TestCustomResource customResource = testCustomResource();
    overrideData(event.getRelatedCustomResourceID(), customResource);
    PostExecutionControl postExecutionControlWithException =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));
    PostExecutionControl defaultDispatchControl = PostExecutionControl.defaultDispatch();

    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenReturn(postExecutionControlWithException)
        .thenReturn(defaultDispatchControl);

    ArgumentCaptor<ExecutionScope> executionScopeArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionScope.class);

    eventProcessorWithRetry.handleEvent(event);
    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .handleExecution(any());
    waitUntilProcessingFinished(eventProcessorWithRetry, event.getRelatedCustomResourceID());

    eventProcessorWithRetry.handleEvent(event);
    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(any());
    waitUntilProcessingFinished(eventProcessorWithRetry, event.getRelatedCustomResourceID());

    eventProcessorWithRetry.handleEvent(event);
    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(3))
        .handleExecution(executionScopeArgumentCaptor.capture());
    waitUntilProcessingFinished(eventProcessorWithRetry, event.getRelatedCustomResourceID());
    log.info("Finished successfulExecutionResetsTheRetry");


    List<ExecutionScope> executionScopes = executionScopeArgumentCaptor.getAllValues();

    assertThat(executionScopes).hasSize(3);
    assertThat(executionScopes.get(0).getRetryInfo()).isNull();
    assertThat(executionScopes.get(2).getRetryInfo()).isNull();
    assertThat(executionScopes.get(1).getRetryInfo().getAttemptCount()).isEqualTo(1);
    assertThat(executionScopes.get(1).getRetryInfo().isLastAttempt()).isEqualTo(false);
  }

  private void waitUntilProcessingFinished(EventProcessor eventProcessor,
      ResourceID relatedCustomResourceID) {
    await().atMost(Duration.ofSeconds(3))
        .until(() -> !eventProcessor.isUnderProcessing(relatedCustomResourceID));
  }

  @Test
  void scheduleTimedEventIfInstructedByPostExecutionControl() {
    var testDelay = 10000L;
    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch().withReSchedule(testDelay));

    eventProcessor.handleEvent(prepareCREvent());

    verify(retryTimerEventSourceMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .scheduleOnce((ResourceID) any(), eq(testDelay));
  }

  @Test
  void reScheduleOnlyIfNotExecutedEventsReceivedMeanwhile() throws InterruptedException {
    var testDelay = 10000L;
    doAnswer(new AnswersWithDelay(FAKE_CONTROLLER_EXECUTION_DURATION,
        new Returns(PostExecutionControl.defaultDispatch().withReSchedule(testDelay))))
        .when(reconciliationDispatcherMock).handleExecution(any());
    var resourceId = new ResourceID("test1", "default");
    eventProcessor.handleEvent(prepareCREvent(resourceId));
    Thread.sleep(FAKE_CONTROLLER_EXECUTION_DURATION / 3);
    eventProcessor.handleEvent(prepareCREvent(resourceId));

    verify(retryTimerEventSourceMock,
        after((long) (FAKE_CONTROLLER_EXECUTION_DURATION * 1.5)).times(0))
        .scheduleOnce((ResourceID) any(), eq(testDelay));
  }

  @Test
  void doNotFireEventsIfClosing() {
    eventProcessor.stop();
    eventProcessor.handleEvent(prepareCREvent());

    verify(reconciliationDispatcherMock, after(50).times(0)).handleExecution(any());
  }

  @Test
  void cancelScheduleOnceEventsOnSuccessfulExecution() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    var cr = testCustomResource(crID);

    eventProcessor.eventProcessingFinished(new ExecutionScope(null).setResource(cr),
        PostExecutionControl.defaultDispatch());

    verify(retryTimerEventSourceMock, times(1)).cancelOnceSchedule(eq(crID));
  }

  @Test
  void startProcessedMarkedEventReceivedBefore() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    eventProcessor =
        spy(new EventProcessor(controllerConfiguration(null,
            LinearRateLimiter.deactivatedRateLimiter()), reconciliationDispatcherMock,
            eventSourceManagerMock,
            metricsMock));
    when(controllerResourceEventSourceMock.get(eq(crID)))
        .thenReturn(Optional.of(testCustomResource()));
    eventProcessor.handleEvent(new Event(crID));

    verify(reconciliationDispatcherMock, timeout(100).times(0)).handleExecution(any());

    eventProcessor.start();

    verify(reconciliationDispatcherMock, timeout(100).times(1)).handleExecution(any());
    verify(metricsMock, times(1)).reconcileCustomResource(any(HasMetadata.class), isNull(), any());
  }

  @Test
  void updatesEventSourceHandlerIfResourceUpdated() {
    TestCustomResource customResource = testCustomResource();
    ExecutionScope executionScope =
        new ExecutionScope(null).setResource(customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.customResourceUpdated(customResource);

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);


    verify(controllerResourceEventSourceMock, times(1)).handleRecentResourceUpdate(any(), any(),
        any());
  }

  @Test
  void notUpdatesEventSourceHandlerIfResourceUpdated() {
    TestCustomResource customResource = testCustomResource();
    ExecutionScope executionScope =
        new ExecutionScope(null).setResource(customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.customResourceStatusPatched(customResource);

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(controllerResourceEventSourceMock, times(0)).handleRecentResourceUpdate(any(), any(),
        any());
  }

  @Test
  void notReschedulesAfterTheFinalizerRemoveProcessed() {
    TestCustomResource customResource = testCustomResource();
    markForDeletion(customResource);
    ExecutionScope executionScope =
        new ExecutionScope(null).setResource(customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.customResourceFinalizerRemoved(customResource);

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(reconciliationDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  @Test
  void skipEventProcessingIfFinalizerRemoveProcessed() {
    TestCustomResource customResource = testCustomResource();
    markForDeletion(customResource);
    ExecutionScope executionScope =
        new ExecutionScope(null).setResource(customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.customResourceFinalizerRemoved(customResource);

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);
    eventProcessorWithRetry.handleEvent(prepareCREvent(customResource));

    verify(reconciliationDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  /**
   * Cover corner case when a delete event missed and a new resource with same ResourceID is created
   */
  @Test
  void newResourceAfterMissedDeleteEvent() {
    TestCustomResource customResource = testCustomResource();
    markForDeletion(customResource);
    ExecutionScope executionScope =
        new ExecutionScope(null).setResource(customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.customResourceFinalizerRemoved(customResource);
    var newResource = testCustomResource();
    newResource.getMetadata().setName(customResource.getMetadata().getName());

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);
    eventProcessorWithRetry.handleEvent(prepareCREvent(newResource));

    verify(reconciliationDispatcherMock, timeout(50).times(1)).handleExecution(any());
  }

  @Test
  void rateLimitsReconciliationSubmission() {
    // the refresh period value does not matter here
    var refreshPeriod = Duration.ofMillis(100);
    var event = prepareCREvent();

    final var rateLimit = new RateLimitState() {};
    when(rateLimiterMock.initState()).thenReturn(rateLimit);
    when(rateLimiterMock.isLimited(rateLimit))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(refreshPeriod));

    eventProcessor.handleEvent(event);
    verify(reconciliationDispatcherMock, after(FAKE_CONTROLLER_EXECUTION_DURATION).times(1))
        .handleExecution(any());
    verify(retryTimerEventSourceMock, times(0)).scheduleOnce((ResourceID) any(), anyLong());

    eventProcessor.handleEvent(event);
    verify(retryTimerEventSourceMock, times(1)).scheduleOnce((ResourceID) any(), anyLong());
  }

  @Test
  void schedulesRetryForMarReconciliationInterval() {
    TestCustomResource customResource = testCustomResource();
    ExecutionScope executionScope =
        new ExecutionScope(null).setResource(customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.defaultDispatch();

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(retryTimerEventSourceMock, times(1)).scheduleOnce((ResourceID) any(), anyLong());
  }

  @Test
  void schedulesRetryForMarReconciliationIntervalIfRetryExhausted() {
    RetryExecution mockRetryExecution = mock(RetryExecution.class);
    when(mockRetryExecution.nextDelay()).thenReturn(Optional.empty());
    Retry retry = mock(Retry.class);
    when(retry.initExecution()).thenReturn(mockRetryExecution);
    eventProcessorWithRetry =
        spy(new EventProcessor(controllerConfiguration(retry,
            LinearRateLimiter.deactivatedRateLimiter()), reconciliationDispatcherMock,
            eventSourceManagerMock,
            metricsMock));
    eventProcessorWithRetry.start();
    ExecutionScope executionScope =
        new ExecutionScope(null).setResource(testCustomResource());
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException());
    when(eventProcessorWithRetry.retryEventSource()).thenReturn(retryTimerEventSourceMock);

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(retryTimerEventSourceMock, times(1)).scheduleOnce((ResourceID) any(), anyLong());
  }

  @Test
  void executionOfReconciliationShouldNotStartIfProcessorStopped() throws InterruptedException {
    when(reconciliationDispatcherMock.handleExecution(any()))
        .then((Answer<PostExecutionControl>) invocationOnMock -> {
          Thread.sleep(DISPATCHING_DELAY);
          return PostExecutionControl.defaultDispatch();
        });

    final var configurationService = ConfigurationService.newOverriddenConfigurationService(
        new BaseConfigurationService(),
        o -> {
          o.withConcurrentReconciliationThreads(1);
          o.withMinConcurrentReconciliationThreads(1);
        });
    eventProcessor =
            spy(new EventProcessor(controllerConfiguration(null, rateLimiterMock, configurationService),
                    reconciliationDispatcherMock,
                    eventSourceManagerMock, null));
    eventProcessor.start();

    eventProcessor.handleEvent(prepareCREvent());
    eventProcessor.handleEvent(prepareCREvent());
    eventProcessor.stop();

    // wait until both event should be handled
    Thread.sleep(TIME_TO_WAIT_AFTER_SUBMISSION_BEFORE_EXECUTION + 2 * DISPATCHING_DELAY);
    verify(reconciliationDispatcherMock, atMostOnce())
        .handleExecution(any());
  }

  @Test
  void cleansUpForDeleteEventEvenIfProcessorNotStarted() {
    ResourceID resourceID = new ResourceID("test1", "default");

    eventProcessor =
        spy(new EventProcessor(controllerConfiguration(null, rateLimiterMock),
            reconciliationDispatcherMock,
            eventSourceManagerMock, null));

    eventProcessor.handleEvent(prepareCREvent(resourceID));
    eventProcessor.handleEvent(new ResourceEvent(ResourceAction.DELETED, resourceID, null));
    eventProcessor.handleEvent(prepareCREvent(resourceID));
    // no exception thrown
  }

  private ResourceID eventAlreadyUnderProcessing() {
    when(reconciliationDispatcherMock.handleExecution(any()))
        .then(
            (Answer<PostExecutionControl>) invocationOnMock -> {
              Thread.sleep(FAKE_CONTROLLER_EXECUTION_DURATION);
              return PostExecutionControl.defaultDispatch();
            });
    Event event = prepareCREvent();
    eventProcessor.handleEvent(event);
    return event.getRelatedCustomResourceID();
  }

  private ResourceEvent prepareCREvent() {
    return prepareCREvent(new ResourceID(UUID.randomUUID().toString(), TEST_NAMESPACE));
  }

  private ResourceEvent prepareCREvent(HasMetadata hasMetadata) {
    when(controllerResourceEventSourceMock.get(eq(ResourceID.fromResource(hasMetadata))))
        .thenReturn(Optional.of(hasMetadata));
    return new ResourceEvent(ResourceAction.UPDATED,
        ResourceID.fromResource(hasMetadata), hasMetadata);
  }

  private ResourceEvent prepareCREvent(ResourceID resourceID) {
    TestCustomResource customResource = testCustomResource(resourceID);
    when(controllerResourceEventSourceMock.get(eq(resourceID)))
        .thenReturn(Optional.of(customResource));
    return new ResourceEvent(ResourceAction.UPDATED,
        ResourceID.fromResource(customResource), customResource);
  }

  private Event nonCREvent(ResourceID relatedCustomResourceUid) {
    return new Event(relatedCustomResourceUid);
  }

  private void overrideData(ResourceID id, HasMetadata applyTo) {
    applyTo.getMetadata().setName(id.getName());
    applyTo.getMetadata().setNamespace(id.getNamespace().orElse(null));
  }

  ControllerConfiguration controllerConfiguration(Retry retry, RateLimiter rateLimiter) {
    return controllerConfiguration(retry, rateLimiter, new BaseConfigurationService());
  }

  ControllerConfiguration controllerConfiguration(Retry retry, RateLimiter rateLimiter,
      ConfigurationService configurationService) {
    ControllerConfiguration res = mock(ControllerConfiguration.class);
    when(res.getName()).thenReturn("Test");
    when(res.getRetry()).thenReturn(retry);
    when(res.getRateLimiter()).thenReturn(rateLimiter);
    when(res.maxReconciliationInterval()).thenReturn(Optional.of(Duration.ofMillis(1000)));
    when(res.getConfigurationService()).thenReturn(configurationService);
    return res;
  }

}
