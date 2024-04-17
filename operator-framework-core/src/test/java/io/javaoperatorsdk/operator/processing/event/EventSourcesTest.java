package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class EventSourcesTest {

  public static final String EVENT_SOURCE_NAME = "foo";

  @Test
  void cannotAddTwoDifferentEventSourcesWithSameName() {
    final var eventSources = new EventSources();
    var es1 = mock(EventSource.class);
    when(es1.name()).thenReturn(EVENT_SOURCE_NAME);
    var es2 = mock(EventSource.class);
    when(es2.name()).thenReturn(EVENT_SOURCE_NAME);

    assertThrows(IllegalArgumentException.class, () -> {
      eventSources.add(es1);
      eventSources.add(es2);
    });
  }

  @Test
  void cannotAddTwoEventSourcesWithSameNameUnlessTheyAreEqual() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    when(source.name()).thenReturn("name");

    eventSources.add(source);
    eventSources.add(source);

    assertThat(eventSources.flatMappedSources())
        .containsExactly(source);
  }


  @Test
  void eventSourcesStreamShouldNotReturnControllerEventSource() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    when(source.name()).thenReturn(EVENT_SOURCE_NAME);

    eventSources.add(source);

    assertThat(eventSources.additionalEventSources()).containsExactly(
        eventSources.retryEventSource(),
        source);
  }

  @Test
  void additionalEventSourcesShouldNotContainNamedEventSources() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    when(source.name()).thenReturn(EVENT_SOURCE_NAME);
    eventSources.add(source);

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
        eventSources.controllerResourceEventSource());
  }

  @Test
  void flatMappedSourcesShouldReturnOnlyUserRegisteredEventSources() {
    final var eventSources = new EventSources();
    final var mock1 =
        eventSourceMockWithName(ResourceEventSource.class, "name1", HasMetadata.class);
    final var mock2 =
        eventSourceMockWithName(ResourceEventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(ResourceEventSource.class, "name3", ConfigMap.class);

    eventSources.add(mock1);
    eventSources.add(mock2);
    eventSources.add(mock3);

    assertThat(eventSources.flatMappedSources()).contains(mock1, mock2, mock3);
  }

  @Test
  void clearShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 =
        eventSourceMockWithName(ResourceEventSource.class, "name1", HasMetadata.class);
    final var mock2 =
        eventSourceMockWithName(ResourceEventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(ResourceEventSource.class, "name3", ConfigMap.class);

    eventSources.add(mock1);
    eventSources.add(mock2);
    eventSources.add(mock3);

    eventSources.clear();
    assertThat(eventSources.flatMappedSources()).isEmpty();
  }

  @Test
  void getShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 =
        eventSourceMockWithName(ResourceEventSource.class, "name1", HasMetadata.class);
    final var mock2 =
        eventSourceMockWithName(ResourceEventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(ResourceEventSource.class, "name2", ConfigMap.class);

    eventSources.add(mock1);

    eventSources.add(mock2);
    eventSources.add(mock2);

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
    final var mock1 =
        eventSourceMockWithName(ResourceEventSource.class, "name1", HasMetadata.class);
    final var mock2 =
        eventSourceMockWithName(ResourceEventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(ResourceEventSource.class, "name3", ConfigMap.class);

    eventSources.add(mock1);
    eventSources.add(mock2);
    eventSources.add(mock3);

    var sources = eventSources.getEventSources(HasMetadata.class);
    assertThat(sources.size()).isEqualTo(2);
    assertThat(sources).contains(mock1, mock2);

    sources = eventSources.getEventSources(ConfigMap.class);
    assertThat(sources.size()).isEqualTo(1);
    assertThat(sources).contains(mock3);

    assertThat(eventSources.getEventSources(Service.class)).isEmpty();
  }



  <T extends ResourceEventSource> EventSource eventSourceMockWithName(Class<T> clazz, String name,
      Class resourceType) {
    var mockedES = mock(clazz);
    when(mockedES.name()).thenReturn(name);
    when(mockedES.resourceType()).thenReturn(resourceType);
    return mockedES;
  }

}
