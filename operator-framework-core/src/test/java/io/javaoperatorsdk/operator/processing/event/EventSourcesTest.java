package io.javaoperatorsdk.operator.processing.event;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"unchecked", "rawtypes"})
class EventSourcesTest {

  EventSources eventSources = new EventSources();

  @Test
  void cannotAddTwoEventSourcesWithSameName() {
    assertThrows(IllegalArgumentException.class, () -> {
      eventSources.add("name", mock(EventSource.class));
      eventSources.add("name", mock(EventSource.class));
    });
  }

  @Test
  void allEventSourcesShouldReturnAll() {
    // initial state doesn't have ControllerResourceEventSource
    assertEquals(Set.of(eventSources.retryEventSource()), eventSources.eventSources().collect(
        Collectors.toSet()));
    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);
    final var controller = new Controller(mock(Reconciler.class), configuration,
        MockKubernetesClient.client(HasMetadata.class));
    eventSources.initControllerEventSource(controller);
    assertEquals(
        Set.of(eventSources.retryEventSource(), eventSources.controllerResourceEventSource()),
        eventSources.eventSources().collect(Collectors.toSet()));
    final var source = mock(EventSource.class);
    eventSources.add("foo", source);
    assertEquals(Set.of(eventSources.retryEventSource(),
        eventSources.controllerResourceEventSource(), source),
        eventSources.eventSources().collect(Collectors.toSet()));
  }

}
