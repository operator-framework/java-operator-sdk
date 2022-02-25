package io.javaoperatorsdk.operator.processing.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.TestUtils.testCustomResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(EventProcessorTest.class);

  public static final int FAKE_CONTROLLER_EXECUTION_DURATION = 250;
  public static final int SEPARATE_EXECUTION_TIMEOUT = 450;
  public static final String TEST_NAMESPACE = "default-event-handler-test";
  private ReconciliationDispatcher reconciliationDispatcherMock =
      mock(ReconciliationDispatcher.class);
  private EventSourceManager eventSourceManagerMock = mock(EventSourceManager.class);
  private TimerEventSource retryTimerEventSourceMock = mock(TimerEventSource.class);
  private ControllerResourceEventSource controllerResourceEventSourceMock =
      mock(ControllerResourceEventSource.class);
  private Metrics metricsMock = mock(Metrics.class);
  private EventProcessor eventProcessor;
  private EventProcessor eventProcessorWithRetry;

  @BeforeEach
  void setup() {
    when(eventSourceManagerMock.getControllerResourceEventSource())
        .thenReturn(controllerResourceEventSourceMock);
    eventProcessor =
        spy(new EventProcessor(reconciliationDispatcherMock, eventSourceManagerMock, "Test", null,
            null));
    eventProcessor.start();
    eventProcessorWithRetry =
        spy(new EventProcessor(reconciliationDispatcherMock, eventSourceManagerMock, "Test",
            GenericRetry.defaultLimitedExponentialRetry(), null));
    eventProcessorWithRetry.start();
    when(eventProcessor.retryEventSource()).thenReturn(retryTimerEventSourceMock);
    when(eventProcessorWithRetry.retryEventSource()).thenReturn(retryTimerEventSourceMock);
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
  void ifExecutionInProgressWaitsUntilItsFinished() throws InterruptedException {
    ResourceID resourceUid = eventAlreadyUnderProcessing();

    eventProcessor.handleEvent(nonCREvent(resourceUid));

    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .handleExecution(any());
  }

  @Test
  void schedulesAnEventRetryOnException() {
    TestCustomResource customResource = testCustomResource();

    ExecutionScope executionScope = new ExecutionScope(customResource, null);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(retryTimerEventSourceMock, times(1))
        .scheduleOnce(eq(customResource), eq(GenericRetry.DEFAULT_INITIAL_INTERVAL));
  }

  @Test
  void executesTheControllerInstantlyAfterErrorIfNewEventsReceived() {
    Event event = prepareCREvent();
    TestCustomResource customResource = testCustomResource();
    overrideData(event.getRelatedCustomResourceID(), customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));

    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenReturn(postExecutionControl)
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
        .scheduleOnce(eq(customResource), eq(GenericRetry.DEFAULT_INITIAL_INTERVAL));
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

    eventProcessorWithRetry.handleEvent(event);
    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(any());

    eventProcessorWithRetry.handleEvent(event);
    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(3))
        .handleExecution(executionScopeArgumentCaptor.capture());
    log.info("Finished successfulExecutionResetsTheRetry");

    List<ExecutionScope> executionScopes = executionScopeArgumentCaptor.getAllValues();

    assertThat(executionScopes).hasSize(3);
    assertThat(executionScopes.get(0).getRetryInfo()).isNull();
    assertThat(executionScopes.get(2).getRetryInfo()).isNull();
    assertThat(executionScopes.get(1).getRetryInfo().getAttemptCount()).isEqualTo(1);
    assertThat(executionScopes.get(1).getRetryInfo().isLastAttempt()).isEqualTo(false);
  }

  @Test
  void scheduleTimedEventIfInstructedByPostExecutionControl() {
    var testDelay = 10000L;
    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch().withReSchedule(testDelay));

    eventProcessor.handleEvent(prepareCREvent());

    verify(retryTimerEventSourceMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .scheduleOnce(any(), eq(testDelay));
  }

  @Test
  void reScheduleOnlyIfNotExecutedEventsReceivedMeanwhile() {
    var testDelay = 10000L;
    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch().withReSchedule(testDelay));

    eventProcessor.handleEvent(prepareCREvent());
    eventProcessor.handleEvent(prepareCREvent());

    verify(retryTimerEventSourceMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(0))
        .scheduleOnce(any(), eq(testDelay));
  }

  @Test
  void doNotFireEventsIfClosing() {
    eventProcessor.stop();
    eventProcessor.handleEvent(prepareCREvent());

    verify(reconciliationDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  @Test
  void cancelScheduleOnceEventsOnSuccessfulExecution() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    var cr = testCustomResource(crID);

    eventProcessor.eventProcessingFinished(new ExecutionScope(cr, null),
        PostExecutionControl.defaultDispatch());

    verify(retryTimerEventSourceMock, times(1)).cancelOnceSchedule(eq(crID));
  }

  @Test
  void startProcessedMarkedEventReceivedBefore() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    eventProcessor =
        spy(new EventProcessor(reconciliationDispatcherMock, eventSourceManagerMock, "Test", null,
            metricsMock));
    when(controllerResourceEventSourceMock.get(eq(crID)))
        .thenReturn(Optional.of(testCustomResource()));
    eventProcessor.handleEvent(new Event(crID));

    verify(reconciliationDispatcherMock, timeout(100).times(0)).handleExecution(any());

    eventProcessor.start();

    verify(reconciliationDispatcherMock, timeout(100).times(1)).handleExecution(any());
    verify(metricsMock, times(1)).reconcileCustomResource(any(), isNull());
  }

  @Test
  void updatesEventSourceHandlerIfResourceUpdated() {
    TestCustomResource customResource = testCustomResource();
    ExecutionScope executionScope = new ExecutionScope(customResource, null);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.customResourceUpdated(customResource);

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);


    verify(controllerResourceEventSourceMock, times(1)).handleRecentResourceUpdate(any(), any());
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

  private ResourceEvent prepareCREvent(ResourceID uid) {
    TestCustomResource customResource = testCustomResource(uid);
    when(controllerResourceEventSourceMock.get(eq(uid))).thenReturn(Optional.of(customResource));
    return new ResourceEvent(ResourceAction.UPDATED,
        ResourceID.fromResource(customResource));
  }

  private Event nonCREvent(ResourceID relatedCustomResourceUid) {
    return new Event(relatedCustomResourceUid);
  }

  private void overrideData(ResourceID id, HasMetadata applyTo) {
    applyTo.getMetadata().setName(id.getName());
    applyTo.getMetadata().setNamespace(id.getNamespace().orElse(null));
  }

}
