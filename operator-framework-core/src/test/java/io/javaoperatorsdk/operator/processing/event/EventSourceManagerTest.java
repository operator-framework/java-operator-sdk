package io.javaoperatorsdk.operator.processing.event;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  public void closeShouldCascadeToEventSources() throws IOException {
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
    assertTrue(eventSourceManager.getResourceEventSourceFor(null).isEmpty());
    assertTrue(eventSourceManager.getResourceEventSourceFor(Class.class).isEmpty());

    // manager is initialized with a controller configured to handle HasMetadata
    EventSourceManager manager = initManager();
    Optional<EventSource> source = manager.getResourceEventSourceFor(HasMetadata.class);
    assertTrue(source.isPresent());
    assertTrue(source.get() instanceof ControllerResourceEventSource);

    CachingEventSource eventSource = mock(CachingEventSource.class);
    when(eventSource.getResourceClass()).thenReturn(String.class);
    manager.registerEventSource(eventSource);

    source = manager.getResourceEventSourceFor(String.class);
    assertTrue(source.isPresent());
    assertEquals(eventSource, source.get());
  }

  @Test
  void shouldNotBePossibleToAddEventSourcesForSameTypeAndQualifier() {
    EventSourceManager manager = initManager();

    CachingEventSource eventSource = mock(CachingEventSource.class);
    when(eventSource.getResourceClass()).thenReturn(String.class);
    var config = mock(EventSourceConfiguration.class);
    when(config.name()).thenReturn("name1");
    when(eventSource.getConfiguration()).thenReturn(config);
    manager.registerEventSource(eventSource);

    eventSource = mock(CachingEventSource.class);
    when(eventSource.getResourceClass()).thenReturn(String.class);
    config = mock(EventSourceConfiguration.class);
    when(config.name()).thenReturn("name1");
    when(eventSource.getConfiguration()).thenReturn(config);
    final var source = eventSource;

    final var exception = assertThrows(OperatorException.class,
        () -> manager.registerEventSource(source));
    final var cause = exception.getCause();
    assertTrue(cause instanceof IllegalArgumentException);
    assertTrue(cause.getMessage().contains(
        "An event source is already registered for the (java.lang.String, name1) class/name combination"));
  }

  @Test
  void retrievingAnEventSourceWhenMultipleAreRegisteredForATypeShouldRequireAQualifier() {
    EventSourceManager manager = initManager();

    CachingEventSource eventSource = mock(CachingEventSource.class);
    when(eventSource.getResourceClass()).thenReturn(String.class);
    var config = mock(EventSourceConfiguration.class);
    when(config.name()).thenReturn("name1");
    when(eventSource.getConfiguration()).thenReturn(config);
    manager.registerEventSource(eventSource);

    CachingEventSource eventSource2 = mock(CachingEventSource.class);
    when(eventSource2.getResourceClass()).thenReturn(String.class);
    config = mock(EventSourceConfiguration.class);
    when(config.name()).thenReturn("name2");
    when(eventSource2.getConfiguration()).thenReturn(config);
    manager.registerEventSource(eventSource2);

    final var exception = assertThrows(IllegalArgumentException.class,
        () -> manager.getResourceEventSourceFor(String.class));
    assertTrue(exception.getMessage().contains("name1"));
    assertTrue(exception.getMessage().contains("name2"));

    assertTrue(manager.getResourceEventSourceFor(String.class, "name2").get().equals(eventSource2));
    assertTrue(manager.getResourceEventSourceFor(String.class, "name1").get().equals(eventSource));
  }

  @Test
  void timerAndControllerEventSourcesShouldBeListedFirst() {
    EventSourceManager manager = initManager();

    CachingEventSource eventSource = mock(CachingEventSource.class);
    when(eventSource.getResourceClass()).thenReturn(String.class);
    manager.registerEventSource(eventSource);

    final Set<EventSource> sources = manager.getRegisteredEventSources();
    assertEquals(3, sources.size());
    final Iterator<EventSource> iterator = sources.iterator();
    for (int i = 0; i < sources.size(); i++) {
      final EventSource source = iterator.next();
      switch (i) {
        case 0:
          assertTrue(source instanceof TimerEventSource);
          break;
        case 1:
          assertTrue(source instanceof ControllerResourceEventSource);
          break;
        case 2:
          assertTrue(source instanceof CachingEventSource);
          break;
        default:
          fail();
      }
    }
  }

  private EventSourceManager initManager() {
    final Controller controller = mock(Controller.class);
    final ControllerConfiguration configuration = mock(ControllerConfiguration.class);
    when(configuration.getResourceClass()).thenReturn(HasMetadata.class);
    when(configuration.getConfigurationService()).thenReturn(mock(ConfigurationService.class));
    when(controller.getConfiguration()).thenReturn(configuration);
    ExecutorServiceManager.init(configuration.getConfigurationService());
    var manager = new EventSourceManager(controller);
    return manager;
  }
}
