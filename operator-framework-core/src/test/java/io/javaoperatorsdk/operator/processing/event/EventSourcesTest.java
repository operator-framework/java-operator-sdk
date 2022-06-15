package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.processing.event.EventSources.RETRY_RESCHEDULE_TIMER_EVENT_SOURCE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"unchecked", "rawtypes"})
class EventSourcesTest {

  public static final String EVENT_SOURCE_NAME = "foo";
  EventSources eventSources = new EventSources();

  @Test
  void cannotAddTwoEventSourcesWithSameName() {
    assertThrows(IllegalArgumentException.class, () -> {
      eventSources.add("name", mock(EventSource.class));
      eventSources.add("name", mock(EventSource.class));
    });
  }


  @Test
  void eventSourcesStreamShouldNotReturnControllerEventSource() {
    initControllerEventSource();
    final var source = mock(EventSource.class);
    eventSources.add(EVENT_SOURCE_NAME, source);

    assertThat(eventSources.additionalNamedEventSources()).containsExactly(
        new NamedEventSource(eventSources.retryEventSource(),
            RETRY_RESCHEDULE_TIMER_EVENT_SOURCE_NAME),
        new NamedEventSource(source, EVENT_SOURCE_NAME));
  }

  private void initControllerEventSource() {
    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);
    final var controller = new Controller(mock(Reconciler.class), configuration,
        MockKubernetesClient.client(HasMetadata.class));
    eventSources.initControllerEventSource(controller);
  }

}
