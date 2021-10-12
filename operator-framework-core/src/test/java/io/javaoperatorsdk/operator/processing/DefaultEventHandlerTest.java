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

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import io.javaoperatorsdk.operator.processing.event.internal.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEvent;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.TestUtils.testCustomResource;
import static io.javaoperatorsdk.operator.processing.event.internal.ResourceAction.DELETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultEventHandlerTest {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventHandlerTest.class);

  public static final int FAKE_CONTROLLER_EXECUTION_DURATION = 250;
  public static final int SEPARATE_EXECUTION_TIMEOUT = 450;
  public static final String TEST_NAMESPACE = "default-event-handler-test";
  private EventDispatcher eventDispatcherMock = mock(EventDispatcher.class);
  private DefaultEventSourceManager defaultEventSourceManagerMock =
      mock(DefaultEventSourceManager.class);
  private ResourceCache resourceCache = mock(ResourceCache.class);

  private TimerEventSource retryTimerEventSourceMock = mock(TimerEventSource.class);

  private DefaultEventHandler defaultEventHandler =
      new DefaultEventHandler(eventDispatcherMock, resourceCache, "Test", null);

  private DefaultEventHandler defaultEventHandlerWithRetry =
      new DefaultEventHandler(eventDispatcherMock, resourceCache, "Test",
          GenericRetry.defaultLimitedExponentialRetry());

  @BeforeEach
  public void setup() {
    when(defaultEventSourceManagerMock.getRetryTimerEventSource())
        .thenReturn(retryTimerEventSourceMock);
    defaultEventHandler.setEventSourceManager(defaultEventSourceManagerMock);
    defaultEventHandlerWithRetry.setEventSourceManager(defaultEventSourceManagerMock);
  }

  @Test
  public void dispatchesEventsIfNoExecutionInProgress() {
    defaultEventHandler.handleEvent(prepareCREvent());

    verify(eventDispatcherMock, timeout(50).times(1)).handleExecution(any());
  }

  @Test
  public void skipProcessingIfLatestCustomResourceNotInCache() {
    Event event = prepareCREvent();
    when(resourceCache.getCustomResource(event.getRelatedCustomResourceID()))
        .thenReturn(Optional.empty());

    defaultEventHandler.handleEvent(event);

    verify(eventDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  @Test
  public void ifExecutionInProgressWaitsUntilItsFinished() throws InterruptedException {
    CustomResourceID resourceUid = eventAlreadyUnderProcessing();

    defaultEventHandler.handleEvent(nonCREvent(resourceUid));

    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .handleExecution(any());
  }

  @Test
  public void buffersAllIncomingEventsWhileControllerInExecution() {
    CustomResourceID resourceUid = eventAlreadyUnderProcessing();

    defaultEventHandler.handleEvent(nonCREvent(resourceUid));
    defaultEventHandler.handleEvent(prepareCREvent(resourceUid));

    ArgumentCaptor<ExecutionScope> captor = ArgumentCaptor.forClass(ExecutionScope.class);
    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(captor.capture());
    List<Event> events = captor.getAllValues().get(1).getEvents();
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(TimerEvent.class);
    assertThat(events.get(1)).isInstanceOf(CustomResourceEvent.class);
  }

  @Test
  public void cleanUpAfterDeleteEvent() {
    TestCustomResource customResource = testCustomResource();
    when(resourceCache.getCustomResource(CustomResourceID.fromResource(customResource)))
        .thenReturn(Optional.of(customResource));
    CustomResourceEvent event =
        new CustomResourceEvent(DELETED, customResource);

    defaultEventHandler.handleEvent(event);

    waitMinimalTime();
    verify(defaultEventSourceManagerMock, times(1))
        .cleanupForCustomResource(CustomResourceID.fromResource(customResource));
  }

  @Test
  public void schedulesAnEventRetryOnException() {
    Event event = prepareCREvent();
    TestCustomResource customResource = testCustomResource();

    ExecutionScope executionScope = new ExecutionScope(List.of(event), customResource, null);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));

    defaultEventHandlerWithRetry.eventProcessingFinished(executionScope, postExecutionControl);

    verify(retryTimerEventSourceMock, times(1))
        .scheduleOnce(eq(customResource), eq(GenericRetry.DEFAULT_INITIAL_INTERVAL));
  }

  @Test
  public void executesTheControllerInstantlyAfterErrorIfEventsBuffered() {
    Event event = prepareCREvent();
    TestCustomResource customResource = testCustomResource();
    overrideData(event.getRelatedCustomResourceID(), customResource);
    PostExecutionControl postExecutionControl =
        PostExecutionControl.exceptionDuringExecution(new RuntimeException("test"));

    when(eventDispatcherMock.handleExecution(any()))
        .thenReturn(postExecutionControl)
        .thenReturn(PostExecutionControl.defaultDispatch());

    // start processing an event
    defaultEventHandlerWithRetry.handleEvent(event);
    // buffer another event
    defaultEventHandlerWithRetry.handleEvent(event);

    ArgumentCaptor<ExecutionScope> executionScopeArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionScope.class);
    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(executionScopeArgumentCaptor.capture());
    List<ExecutionScope> allValues = executionScopeArgumentCaptor.getAllValues();
    assertThat(allValues).hasSize(2);
    assertThat(allValues.get(1).getEvents()).hasSize(2);
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

    when(eventDispatcherMock.handleExecution(any()))
        .thenReturn(postExecutionControlWithException)
        .thenReturn(defaultDispatchControl);

    ArgumentCaptor<ExecutionScope> executionScopeArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionScope.class);

    defaultEventHandlerWithRetry.handleEvent(event);
    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .handleExecution(any());

    defaultEventHandlerWithRetry.handleEvent(event);
    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(any());

    defaultEventHandlerWithRetry.handleEvent(event);
    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(3))
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
    when(eventDispatcherMock.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch().withReSchedule(testDelay));

    defaultEventHandler.handleEvent(prepareCREvent());

    verify(retryTimerEventSourceMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .scheduleOnce(any(), eq(testDelay));
  }

  @Test
  public void reScheduleOnlyIfNotExecutedBufferedEvents() {
    var testDelay = 10000L;
    when(eventDispatcherMock.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch().withReSchedule(testDelay));

    defaultEventHandler.handleEvent(prepareCREvent());
    defaultEventHandler.handleEvent(prepareCREvent());

    verify(retryTimerEventSourceMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(0))
        .scheduleOnce(any(), eq(testDelay));
  }

  @Test
  public void doNotFireEventsIfClosing() {
    defaultEventHandler.close();
    defaultEventHandler.handleEvent(prepareCREvent());

    verify(eventDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  private void waitMinimalTime() {
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private CustomResourceID eventAlreadyUnderProcessing() {
    when(eventDispatcherMock.handleExecution(any()))
        .then(
            (Answer<PostExecutionControl>) invocationOnMock -> {
              Thread.sleep(FAKE_CONTROLLER_EXECUTION_DURATION);
              return PostExecutionControl.defaultDispatch();
            });
    Event event = prepareCREvent();
    defaultEventHandler.handleEvent(event);
    return event.getRelatedCustomResourceID();
  }

  private CustomResourceEvent prepareCREvent() {
    return prepareCREvent(new CustomResourceID(UUID.randomUUID().toString(), TEST_NAMESPACE));
  }

  private CustomResourceEvent prepareCREvent(CustomResourceID uid) {
    TestCustomResource customResource = testCustomResource(uid);
    when(resourceCache.getCustomResource(eq(uid))).thenReturn(Optional.of(customResource));
    return new CustomResourceEvent(ResourceAction.UPDATED, customResource);
  }

  private Event nonCREvent(CustomResourceID relatedCustomResourceUid) {
    return new TimerEvent(relatedCustomResourceUid);
  }

  private void overrideData(CustomResourceID id, CustomResource<?, ?> applyTo) {
    applyTo.getMetadata().setName(id.getName());
    applyTo.getMetadata().setNamespace(id.getNamespace().orElse(null));
  }

}
