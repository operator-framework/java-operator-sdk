package io.javaoperatorsdk.operator.processing.event;

import static io.javaoperatorsdk.operator.processing.event.EventSources.RETRY_RESCHEDULE_TIMER_EVENT_SOURCE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "rawtypes"})
class EventSourcesTest {

  public static final String EVENT_SOURCE_NAME = "foo";

  @Test
  void cannotAddTwoDifferentEventSourcesWithSameName() {
    final var eventSources = new EventSources();
    assertThrows(IllegalArgumentException.class, () -> {
      eventSources.add(new NamedEventSource(mock(EventSource.class), "name"));
      eventSources.add(new NamedEventSource(mock(EventSource.class), "name"));
    });
  }

  @Test
  void cannotAddTwoEventSourcesWithSameNameUnlessTheyAreEqual() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    eventSources.add(new NamedEventSource(source, "name"));
    eventSources.add(new NamedEventSource(source, "name"));
    assertThat(eventSources.flatMappedSources())
        .containsExactly(new NamedEventSource(source, "name"));
  }


  @Test
  void eventSourcesStreamShouldNotReturnControllerEventSource() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    final var namedEventSource = new NamedEventSource(source, EVENT_SOURCE_NAME);
    eventSources.add(namedEventSource);

    assertThat(eventSources.additionalNamedEventSources()).containsExactly(
        new NamedEventSource(eventSources.retryEventSource(),
            RETRY_RESCHEDULE_TIMER_EVENT_SOURCE_NAME),
        namedEventSource);
  }

  @Test
  void additionalEventSourcesShouldNotContainNamedEventSources() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    final var namedEventSource = new NamedEventSource(source, EVENT_SOURCE_NAME);
    eventSources.add(namedEventSource);

    assertThat(eventSources.additionalEventSources()).containsExactly(
        eventSources.retryEventSource(), source);
  }

  @Test
  void checkControllerResourceEventSource() {
    final var eventSources = new EventSources();
    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);
    when(configuration.getConfigurationService()).thenReturn(new BaseConfigurationService());
    final var controller = new Controller(mock(Reconciler.class), configuration,
        MockKubernetesClient.client(HasMetadata.class));
    eventSources.createControllerEventSource(controller);
    final var controllerResourceEventSource = eventSources.controllerResourceEventSource();
    assertNotNull(controllerResourceEventSource);
    assertEquals(HasMetadata.class, controllerResourceEventSource.resourceType());

    assertEquals(controllerResourceEventSource,
        eventSources.namedControllerResourceEventSource().eventSource());
  }

  @Test
  void flatMappedSourcesShouldReturnOnlyUserRegisteredEventSources() {
    final var eventSources = new EventSources();
    final var mock1 = mock(ResourceEventSource.class);
    when(mock1.resourceType()).thenReturn(HasMetadata.class);
    final var mock2 = mock(ResourceEventSource.class);
    when(mock2.resourceType()).thenReturn(HasMetadata.class);
    final var mock3 = mock(ResourceEventSource.class);
    when(mock3.resourceType()).thenReturn(ConfigMap.class);

    final var named1 = new NamedEventSource(mock1, "name1");
    final var named2 = new NamedEventSource(mock2, "name2");
    final var named3 = new NamedEventSource(mock3, "name2");
    eventSources.add(named1);
    eventSources.add(named2);
    eventSources.add(named3);

    assertThat(eventSources.flatMappedSources()).contains(named1, named2, named3);
  }

  @Test
  void clearShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 = mock(ResourceEventSource.class);
    when(mock1.resourceType()).thenReturn(HasMetadata.class);
    final var mock2 = mock(ResourceEventSource.class);
    when(mock2.resourceType()).thenReturn(HasMetadata.class);
    final var mock3 = mock(ResourceEventSource.class);
    when(mock3.resourceType()).thenReturn(ConfigMap.class);

    final var named1 = new NamedEventSource(mock1, "name1");
    final var named2 = new NamedEventSource(mock2, "name2");
    final var named3 = new NamedEventSource(mock3, "name2");
    eventSources.add(named1);
    eventSources.add(named2);
    eventSources.add(named3);

    eventSources.clear();
    assertThat(eventSources.flatMappedSources()).isEmpty();
  }

  @Test
  void getShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 = mock(ResourceEventSource.class);
    when(mock1.resourceType()).thenReturn(HasMetadata.class);
    final var mock2 = mock(ResourceEventSource.class);
    when(mock2.resourceType()).thenReturn(HasMetadata.class);
    final var mock3 = mock(ResourceEventSource.class);
    when(mock3.resourceType()).thenReturn(ConfigMap.class);

    final var named1 = new NamedEventSource(mock1, "name1");
    final var named2 = new NamedEventSource(mock2, "name2");
    final var named3 = new NamedEventSource(mock3, "name2");
    eventSources.add(named1);
    eventSources.add(named2);
    eventSources.add(named3);

    assertEquals(mock1, eventSources.get(HasMetadata.class, "name1"));
    assertEquals(mock2, eventSources.get(HasMetadata.class, "name2"));
    assertEquals(mock3, eventSources.get(ConfigMap.class, "name2"));
    assertEquals(mock3, eventSources.get(ConfigMap.class, null));


    assertThrows(IllegalArgumentException.class, () -> eventSources.get(HasMetadata.class, null));
    assertThrows(IllegalArgumentException.class,
        () -> eventSources.get(ConfigMap.class, "unknown"));
    assertThrows(IllegalArgumentException.class, () -> eventSources.get(null, null));
    assertThrows(IllegalArgumentException.class, () -> eventSources.get(HasMetadata.class, null));
  }

  @Test
  void getEventSourcesShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 = mock(ResourceEventSource.class);
    when(mock1.resourceType()).thenReturn(HasMetadata.class);
    final var mock2 = mock(ResourceEventSource.class);
    when(mock2.resourceType()).thenReturn(HasMetadata.class);
    final var mock3 = mock(ResourceEventSource.class);
    when(mock3.resourceType()).thenReturn(ConfigMap.class);

    final var named1 = new NamedEventSource(mock1, "name1");
    final var named2 = new NamedEventSource(mock2, "name2");
    final var named3 = new NamedEventSource(mock3, "name2");
    eventSources.add(named1);
    eventSources.add(named2);
    eventSources.add(named3);

    var sources = eventSources.getEventSources(HasMetadata.class);
    assertThat(sources.size()).isEqualTo(2);
    assertThat(sources).contains(mock1, mock2);

    sources = eventSources.getEventSources(ConfigMap.class);
    assertThat(sources.size()).isEqualTo(1);
    assertThat(sources).contains(mock3);

    assertThat(eventSources.getEventSources(Service.class)).isEmpty();
  }
}
