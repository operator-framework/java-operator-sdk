package io.javaoperatorsdk.operator.processing;

import static io.javaoperatorsdk.operator.TestUtils.testCustomResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEvent;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

class DefaultEventHandlerTest {

  public static final int FAKE_CONTROLLER_EXECUTION_DURATION = 250;
  public static final int SEPARATE_EXECUTION_TIMEOUT = 450;
  private EventDispatcher eventDispatcherMock = mock(EventDispatcher.class);
  private CustomResourceCache customResourceCache = new CustomResourceCache();
  private DefaultEventHandler defaultEventHandler =
      new DefaultEventHandler(customResourceCache, eventDispatcherMock, "Test", null);
  private DefaultEventSourceManager defaultEventSourceManagerMock =
      mock(DefaultEventSourceManager.class);

  @BeforeEach
  public void setup() {
    defaultEventHandler.setDefaultEventSourceManager(defaultEventSourceManagerMock);
  }

  @Test
  public void dispatchesEventsIfNoExecutionInProgress() {
    defaultEventHandler.handleEvent(prepareCREvent());

    verify(eventDispatcherMock, timeout(50).times(1)).handleExecution(any());
  }

  @Test
  public void skipProcessingIfLatestCustomResourceNotInCache() {
    Event event = prepareCREvent();
    customResourceCache.cleanup(event.getRelatedCustomResourceUid());

    defaultEventHandler.handleEvent(event);

    verify(eventDispatcherMock, timeout(50).times(0)).handleExecution(any());
  }

  @Test
  public void ifExecutionInProgressWaitsUntilItsFinished() throws InterruptedException {
    String resourceUid = eventAlreadyUnderProcessing();

    defaultEventHandler.handleEvent(nonCREvent(resourceUid));

    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(1))
        .handleExecution(any());
  }

  @Test
  public void buffersAllIncomingEventsWhileControllerInExecution() {
    String resourceUid = eventAlreadyUnderProcessing();

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
    customResourceCache.cacheResource(customResource);
    CustomResourceEvent event =
        new CustomResourceEvent(Watcher.Action.DELETED, customResource, null);
    String uid = customResource.getMetadata().getUid();

    defaultEventHandler.handleEvent(event);
    // todo awaitility?
    waitMinimalTime();

    verify(defaultEventSourceManagerMock, times(1)).cleanup(uid);
    assertThat(customResourceCache.getLatestResource(uid)).isNotPresent();
  }

  private void waitMinimalTime() {
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private String eventAlreadyUnderProcessing() {
    when(eventDispatcherMock.handleExecution(any()))
        .then(
            (Answer<PostExecutionControl>)
                invocationOnMock -> {
                  Thread.sleep(FAKE_CONTROLLER_EXECUTION_DURATION);
                  return PostExecutionControl.defaultDispatch();
                });
    Event event = prepareCREvent();
    defaultEventHandler.handleEvent(event);
    return event.getRelatedCustomResourceUid();
  }

  private CustomResourceEvent prepareCREvent() {
    return prepareCREvent(UUID.randomUUID().toString());
  }

  private CustomResourceEvent prepareCREvent(String uid) {
    TestCustomResource customResource = testCustomResource(uid);
    customResourceCache.cacheResource(customResource);
    return new CustomResourceEvent(Watcher.Action.MODIFIED, customResource, null);
  }

  private Event nonCREvent(String relatedCustomResourceUid) {
    TimerEvent timerEvent = new TimerEvent(relatedCustomResourceUid, null);
    return timerEvent;
  }
}
