package io.javaoperatorsdk.operator.processing;

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
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.internal.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.TestUtils.testCustomResource;
import static io.javaoperatorsdk.operator.processing.event.internal.ResourceAction.DELETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(EventProcessorTest.class);

  public static final int FAKE_CONTROLLER_EXECUTION_DURATION = 250;
  public static final int SEPARATE_EXECUTION_TIMEOUT = 450;
  public static final String TEST_NAMESPACE = "default-event-handler-test";
  private EventMarker eventMarker = new EventMarker();
  private ReconciliationDispatcher reconciliationDispatcherMock =
      mock(ReconciliationDispatcher.class);
  private EventSourceManager eventSourceManagerMock =
      mock(EventSourceManager.class);
  private ResourceCache resourceCacheMock = mock(ResourceCache.class);

  private TimerEventSource retryTimerEventSourceMock = mock(TimerEventSource.class);

  private EventProcessor eventProcessor =
      new EventProcessor(reconciliationDispatcherMock, resourceCacheMock, "Test", null,
          eventMarker);

  private EventProcessor eventProcessorWithRetry =
      new EventProcessor(reconciliationDispatcherMock, resourceCacheMock, "Test",
          GenericRetry.defaultLimitedExponentialRetry(), eventMarker);

  @BeforeEach
  public void setup() {
    when(eventSourceManagerMock.getRetryAndRescheduleTimerEventSource())
        .thenReturn(retryTimerEventSourceMock);
    eventProcessor.setEventSourceManager(eventSourceManagerMock);
    eventProcessorWithRetry.setEventSourceManager(eventSourceManagerMock);
  }

  @Test
  public void dispatchesEventsIfNoExecutionInProgress() {
    eventProcessor.handleEvent(prepareCREvent());

    verify(reconciliationDispatcherMock, timeout(50).times(1)).handleExecution(any());
  }

  @Test
  public void skipProcessingIfLatestCustomResourceNotInCache() {
    Event event = prepareCREvent();
    when(resourceCacheMock.getCustomResource(event.getRelatedCustomResourceID()))
        .thenReturn(Optional.empty());

    eventProcessor.handleEvent(event);

    verify(reconciliationDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  @Test
  public void ifExecutionInProgressWaitsUntilItsFinished() throws InterruptedException {
    ResourceID resourceUid = eventAlreadyUnderProcessing();

    eventProcessor.handleEvent(nonCREvent(resourceUid));

    verify(reconciliationDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .handleExecution(any());
  }

  @Test
  public void schedulesAnEventRetryOnException() {
    TestCustomResource customResource = testCustomResource();

    ExecutionScope executionScope = new ExecutionScope(customResource, null);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));

    eventProcessorWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(retryTimerEventSourceMock, times(1))
        .scheduleOnce(eq(customResource), eq(GenericRetry.DEFAULT_INITIAL_INTERVAL));
  }

  @Test
  public void executesTheControllerInstantlyAfterErrorIfNewEventsReceived() {
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
  public void successfulExecutionResetsTheRetry() {
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
  public void scheduleTimedEventIfInstructedByPostExecutionControl() {
    var testDelay = 10000L;
    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch().withReSchedule(testDelay));

    eventProcessor.handleEvent(prepareCREvent());

    verify(retryTimerEventSourceMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .scheduleOnce(any(), eq(testDelay));
  }

  @Test
  public void reScheduleOnlyIfNotExecutedEventsReceivedMeanwhile() {
    var testDelay = 10000L;
    when(reconciliationDispatcherMock.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch().withReSchedule(testDelay));

    eventProcessor.handleEvent(prepareCREvent());
    eventProcessor.handleEvent(prepareCREvent());

    verify(retryTimerEventSourceMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(0))
        .scheduleOnce(any(), eq(testDelay));
  }

  @Test
  public void doNotFireEventsIfClosing() {
    eventProcessor.stop();
    eventProcessor.handleEvent(prepareCREvent());

    verify(reconciliationDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  @Test
  public void cleansUpWhenDeleteEventReceivedAndNoEventPresent() {
    Event deleteEvent =
        new CustomResourceEvent(DELETED, prepareCREvent().getRelatedCustomResourceID());

    eventProcessor.handleEvent(deleteEvent);

    verify(eventSourceManagerMock, times(1))
        .cleanupForCustomResource(eq(deleteEvent.getRelatedCustomResourceID()));
  }

  @Test
  public void cleansUpAfterExecutionIfOnlyDeleteEventMarkLeft() {
    var cr = testCustomResource();
    var crEvent = prepareCREvent(ResourceID.fromResource(cr));
    eventMarker.markDeleteEventReceived(crEvent.getRelatedCustomResourceID());
    var executionScope = new ExecutionScope(cr, null);

    eventProcessor.eventProcessingFinished(executionScope,
        PostExecutionControl.defaultDispatch());

    verify(eventSourceManagerMock, times(1))
        .cleanupForCustomResource(eq(crEvent.getRelatedCustomResourceID()));
  }

  @Test
  public void whitelistNextEventIfTheCacheIsNotPropagatedAfterAnUpdate() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    var cr = testCustomResource(crID);
    var updatedCr = testCustomResource(crID);
    updatedCr.getMetadata().setResourceVersion("2");
    var mockCREventSource = mock(CustomResourceEventSource.class);
    eventMarker.markEventReceived(crID);
    when(resourceCacheMock.getCustomResource(eq(crID))).thenReturn(Optional.of(cr));
    when(eventSourceManagerMock.getCustomResourceEventSource())
        .thenReturn(mockCREventSource);

    eventProcessor.eventProcessingFinished(new ExecutionScope(cr, null),
        PostExecutionControl.customResourceUpdated(updatedCr));

    verify(mockCREventSource, times(1)).whitelistNextEvent(eq(crID));
  }

  @Test
  public void dontWhitelistsEventWhenOtherChangeDuringExecution() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    var cr = testCustomResource(crID);
    var updatedCr = testCustomResource(crID);
    updatedCr.getMetadata().setResourceVersion("2");
    var otherChangeCR = testCustomResource(crID);
    otherChangeCR.getMetadata().setResourceVersion("3");
    var mockCREventSource = mock(CustomResourceEventSource.class);
    eventMarker.markEventReceived(crID);
    when(resourceCacheMock.getCustomResource(eq(crID))).thenReturn(Optional.of(otherChangeCR));
    when(eventSourceManagerMock.getCustomResourceEventSource())
        .thenReturn(mockCREventSource);

    eventProcessor.eventProcessingFinished(new ExecutionScope(cr, null),
        PostExecutionControl.customResourceUpdated(updatedCr));

    verify(mockCREventSource, times(0)).whitelistNextEvent(eq(crID));
  }

  @Test
  public void dontWhitelistsEventIfUpdatedEventInCache() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    var cr = testCustomResource(crID);
    var mockCREventSource = mock(CustomResourceEventSource.class);
    eventMarker.markEventReceived(crID);
    when(resourceCacheMock.getCustomResource(eq(crID))).thenReturn(Optional.of(cr));
    when(eventSourceManagerMock.getCustomResourceEventSource())
        .thenReturn(mockCREventSource);

    eventProcessor.eventProcessingFinished(new ExecutionScope(cr, null),
        PostExecutionControl.customResourceUpdated(cr));

    verify(mockCREventSource, times(0)).whitelistNextEvent(eq(crID));
  }

  @Test
  public void cancelScheduleOnceEventsOnSuccessfulExecution() {
    var crID = new ResourceID("test-cr", TEST_NAMESPACE);
    var cr = testCustomResource(crID);

    eventProcessor.eventProcessingFinished(new ExecutionScope(cr, null),
        PostExecutionControl.defaultDispatch());

    verify(retryTimerEventSourceMock, times(1)).cancelOnceSchedule(eq(crID));
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

  private CustomResourceEvent prepareCREvent() {
    return prepareCREvent(new ResourceID(UUID.randomUUID().toString(), TEST_NAMESPACE));
  }

  private CustomResourceEvent prepareCREvent(ResourceID uid) {
    TestCustomResource customResource = testCustomResource(uid);
    when(resourceCacheMock.getCustomResource(eq(uid))).thenReturn(Optional.of(customResource));
    return new CustomResourceEvent(ResourceAction.UPDATED,
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
