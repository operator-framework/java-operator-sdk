package io.javaoperatorsdk.operator.processing.event;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceStartPriority;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class EventSourceManagerTest {

  private final EventSourceManager eventSourceManager = initManager();

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);
    when(eventSource.resourceType()).thenReturn(EventSource.class);
    when(eventSource.name()).thenReturn("name1");

    eventSourceManager.registerEventSource(eventSource);

    final var registeredSources = eventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources).contains(eventSource);

    verify(eventSource, times(1)).setEventHandler(any());
  }

  @Test
  public void closeShouldCascadeToEventSources() {
    EventSource eventSource = mock(EventSource.class);
    when(eventSource.name()).thenReturn("name1");
    when(eventSource.resourceType()).thenReturn(EventSource.class);

    EventSource eventSource2 = mock(TimerEventSource.class);
    when(eventSource2.name()).thenReturn("name2");
    when(eventSource2.resourceType()).thenReturn(AbstractEventSource.class);

    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.stop();

    verify(eventSource, times(1)).stop();
    verify(eventSource2, times(1)).stop();
  }

  @Test
  public void startCascadesToEventSources() {
    EventSource eventSource = mock(EventSource.class);
    when(eventSource.priority()).thenReturn(EventSourceStartPriority.DEFAULT);
    when(eventSource.name()).thenReturn("name1");
    when(eventSource.resourceType()).thenReturn(EventSource.class);
    EventSource eventSource2 = mock(TimerEventSource.class);
    when(eventSource2.priority()).thenReturn(EventSourceStartPriority.DEFAULT);
    when(eventSource2.name()).thenReturn("name2");
    when(eventSource2.resourceType()).thenReturn(AbstractEventSource.class);
    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.start();

    verify(eventSource, times(1)).start();
    verify(eventSource2, times(1)).start();
  }

  @Test
  void retrievingEventSourceForClassShouldWork() {
    assertThatExceptionOfType(NoEventSourceForClassException.class)
        .isThrownBy(() -> eventSourceManager.getEventSourceFor(Class.class));

    // manager is initialized with a controller configured to handle HasMetadata
    EventSourceManager manager = initManager();
    assertThatExceptionOfType(NoEventSourceForClassException.class)
        .isThrownBy(() -> manager.getEventSourceFor(HasMetadata.class, "unknown_name"));

    ManagedInformerEventSource eventSource = mock(ManagedInformerEventSource.class);
    when(eventSource.resourceType()).thenReturn(String.class);
    when(eventSource.name()).thenReturn("name1");
    manager.registerEventSource(eventSource);

    var source = manager.getEventSourceFor(String.class);
    assertThat(source).isNotNull();
    assertEquals(eventSource, source);
  }

  @Test
  void notPossibleAddEventSourcesForSameName() {
    EventSourceManager manager = initManager();
    final var name = "name1";

    ManagedInformerEventSource eventSource = mock(ManagedInformerEventSource.class);
    when(eventSource.name()).thenReturn(name);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    manager.registerEventSource(eventSource);

    eventSource = mock(ManagedInformerEventSource.class);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    when(eventSource.name()).thenReturn(name);
    final var source = eventSource;

    final var exception =
        assertThrows(OperatorException.class, () -> manager.registerEventSource(source));
    final var cause = exception.getCause();
    assertInstanceOf(IllegalArgumentException.class, cause);
    assertThat(cause.getMessage()).contains("is already registered with name");
  }

  @Test
  void retrievingAnEventSourceWhenMultipleAreRegisteredForATypeShouldRequireAQualifier() {
    EventSourceManager manager = initManager();

    ManagedInformerEventSource eventSource = mock(ManagedInformerEventSource.class);
    when(eventSource.resourceType()).thenReturn(TestCustomResource.class);
    when(eventSource.name()).thenReturn("name1");
    manager.registerEventSource(eventSource);

    ManagedInformerEventSource eventSource2 = mock(ManagedInformerEventSource.class);
    when(eventSource2.name()).thenReturn("name2");
    when(eventSource2.resourceType()).thenReturn(TestCustomResource.class);
    manager.registerEventSource(eventSource2);

    final var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.getEventSourceFor(TestCustomResource.class));
    assertTrue(exception.getMessage().contains("name1"));
    assertTrue(exception.getMessage().contains("name2"));

    assertEquals(manager.getEventSourceFor(TestCustomResource.class, "name2"), eventSource2);
    assertEquals(manager.getEventSourceFor(TestCustomResource.class, "name1"), eventSource);
  }

  @Test
  void changesNamespacesOnControllerAndInformerEventSources() {
    String newNamespaces = "new-namespace";

    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);

    final var configService = new BaseConfigurationService();
    when(configuration.getConfigurationService()).thenReturn(configService);

    final Controller controller =
        new Controller(
            mock(Reconciler.class), configuration, MockKubernetesClient.client(HasMetadata.class));

    EventSources eventSources = spy(new EventSources());
    var controllerResourceEventSourceMock = mock(ControllerEventSource.class);
    doReturn(controllerResourceEventSourceMock).when(eventSources).controllerEventSource();
    when(controllerResourceEventSourceMock.allowsNamespaceChanges()).thenCallRealMethod();
    var manager = new EventSourceManager(controller, eventSources);

    InformerEventSourceConfiguration eventSourceConfigurationMock =
        mock(InformerEventSourceConfiguration.class);
    InformerEventSource informerEventSource = mock(InformerEventSource.class);
    when(informerEventSource.name()).thenReturn("ies");
    when(informerEventSource.resourceType()).thenReturn(TestCustomResource.class);
    when(informerEventSource.configuration()).thenReturn(eventSourceConfigurationMock);
    when(informerEventSource.allowsNamespaceChanges()).thenReturn(true);
    manager.registerEventSource(informerEventSource);

    manager.changeNamespaces(Set.of(newNamespaces));

    verify(informerEventSource, times(1)).changeNamespaces(Set.of(newNamespaces));
    verify(controllerResourceEventSourceMock, times(1)).changeNamespaces(Set.of(newNamespaces));
  }

  private EventSourceManager initManager() {
    final var configuration = MockControllerConfiguration.forResource(ConfigMap.class);
    final var configService = new BaseConfigurationService();
    when(configuration.getConfigurationService()).thenReturn(configService);

    final Controller controller =
        new Controller(
            mock(Reconciler.class), configuration, MockKubernetesClient.client(ConfigMap.class));
    return new EventSourceManager(controller);
  }
}
