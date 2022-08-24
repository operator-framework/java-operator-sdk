package io.javaoperatorsdk.operator.processing.event;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class EventSourceManagerTest {

  private final EventSourceManager eventSourceManager = initManager();

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);

    eventSourceManager.registerEventSource(eventSource);

    Set<EventSource> registeredSources = eventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources).contains(eventSource);

    verify(eventSource, times(1)).setEventHandler(any());
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
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> manager.getResourceEventSourceFor(HasMetadata.class, "unknown_name"));

    ManagedInformerEventSource eventSource = mock(ManagedInformerEventSource.class);
    when(eventSource.resourceType()).thenReturn(String.class);
    manager.registerEventSource(eventSource);

    var source = manager.getResourceEventSourceFor(String.class);
    assertThat(source).isNotNull();
    assertEquals(eventSource, source);
  }

  @Test
  void shouldNotBePossibleToAddEventSourcesForSameTypeAndName() {
    EventSourceManager manager = initManager();
    final var name = "name1";

    ManagedInformerEventSource eventSource = mock(ManagedInformerEventSource.class);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    manager.registerEventSource(name, eventSource);

    eventSource = mock(ManagedInformerEventSource.class);
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

    ManagedInformerEventSource eventSource = mock(ManagedInformerEventSource.class);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    manager.registerEventSource("name1", eventSource);

    ManagedInformerEventSource eventSource2 = mock(ManagedInformerEventSource.class);
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

  @Test
  void changesNamespacesOnControllerAndInformerEventSources() {
    String newNamespaces = "new-namespace";

    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);
    final Controller controller = new Controller(mock(Reconciler.class), configuration,
        MockKubernetesClient.client(HasMetadata.class));

    EventSources eventSources = spy(new EventSources());
    var controllerResourceEventSourceMock = mock(ControllerResourceEventSource.class);
    doReturn(controllerResourceEventSourceMock).when(eventSources).controllerResourceEventSource();
    when(controllerResourceEventSourceMock.allowsNamespaceChanges()).thenCallRealMethod();
    var manager = new EventSourceManager(controller, eventSources);

    InformerConfiguration informerConfigurationMock = mock(InformerConfiguration.class);
    when(informerConfigurationMock.followControllerNamespaceChanges()).thenReturn(true);
    InformerEventSource informerEventSource = mock(InformerEventSource.class);
    when(informerEventSource.resourceType()).thenReturn(TestCustomResource.class);
    when(informerEventSource.getConfiguration()).thenReturn(informerConfigurationMock);
    when(informerEventSource.allowsNamespaceChanges()).thenCallRealMethod();
    manager.registerEventSource("ies", informerEventSource);

    manager.changeNamespaces(Set.of(newNamespaces));

    verify(informerEventSource, times(1)).changeNamespaces(Set.of(newNamespaces));
    verify(controllerResourceEventSourceMock, times(1)).changeNamespaces(Set.of(newNamespaces));
  }

  private EventSourceManager initManager() {
    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);
    final Controller controller = new Controller(mock(Reconciler.class), configuration,
        MockKubernetesClient.client(HasMetadata.class));
    return new EventSourceManager(controller);
  }
}
