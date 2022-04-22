package io.javaoperatorsdk.operator.processing.event;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class EventSourceManagerTest {

  private final EventProcessor eventHandler = mock(EventProcessor.class);
  private final EventSourceManager eventSourceManager = new EventSourceManager(eventHandler);

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);

    eventSourceManager.registerEventSource(eventSource);

    Set<EventSource> registeredSources = eventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources).contains(eventSource);

    verify(eventSource, times(1)).setEventHandler(eq(eventSourceManager.getEventHandler()));
  }

  @Test
  public void closeShouldCascadeToEventSources() {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(TimerEventSource.class);

    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.stop();

    verify(eventSource, times(1)).stop();
    verify(eventSource2, times(1)).stop();
  }

  @Test
  public void startCascadesToEventSources() {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(TimerEventSource.class);
    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.start();

    verify(eventSource, times(1)).start();
    verify(eventSource2, times(1)).start();
  }

  @Test
  void retrievingEventSourceForClassShouldWork() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventSourceManager.getResourceEventSourceFor(Class.class));

    // manager is initialized with a controller configured to handle HasMetadata
    EventSourceManager manager = initManager();
    EventSource source = manager.getResourceEventSourceFor(HasMetadata.class);
    assertThat(source).isInstanceOf(ControllerResourceEventSource.class);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> manager.getResourceEventSourceFor(HasMetadata.class, "unknown_name"));

    CachingEventSource eventSource = mock(CachingEventSource.class);
    when(eventSource.resourceType()).thenReturn(String.class);
    manager.registerEventSource(eventSource);

    source = manager.getResourceEventSourceFor(String.class);
    assertThat(source).isNotNull();
    assertEquals(eventSource, source);
  }

  @Test
  void shouldNotBePossibleToAddEventSourcesForSameTypeAndName() {
    EventSourceManager manager = initManager();
    final var name = "name1";

    CachingEventSource eventSource = mock(CachingEventSource.class);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    manager.registerEventSource(name, eventSource);

    eventSource = mock(CachingEventSource.class);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    final var source = eventSource;

    final var exception = assertThrows(OperatorException.class,
        () -> manager.registerEventSource(name, source));
    final var cause = exception.getCause();
    assertTrue(cause instanceof IllegalArgumentException);
    assertThat(cause.getMessage()).contains(
        "An event source is already registered for the (io.javaoperatorsdk.operator.sample.simple.TestCustomResource, "
            + name + ") class/name combination");
  }

  @Test
  void retrievingAnEventSourceWhenMultipleAreRegisteredForATypeShouldRequireAQualifier() {
    EventSourceManager manager = initManager();

    CachingEventSource eventSource = mock(CachingEventSource.class);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    manager.registerEventSource("name1", eventSource);

    CachingEventSource eventSource2 = mock(CachingEventSource.class);
    when(eventSource2.resourceType()).thenReturn(TestCustomResource.class);
    manager.registerEventSource("name2", eventSource2);

    final var exception = assertThrows(IllegalArgumentException.class,
        () -> manager.getResourceEventSourceFor(TestCustomResource.class));
    assertTrue(exception.getMessage().contains("name1"));
    assertTrue(exception.getMessage().contains("name2"));

    assertEquals(manager.getResourceEventSourceFor(TestCustomResource.class, "name2"),
        eventSource2);
    assertEquals(manager.getResourceEventSourceFor(TestCustomResource.class, "name1"),
        eventSource);
  }

  private EventSourceManager initManager() {
    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);
    final Controller controller = new Controller(mock(Reconciler.class), configuration,
        MockKubernetesClient.client(HasMetadata.class));
    return new EventSourceManager(controller);
  }
}
