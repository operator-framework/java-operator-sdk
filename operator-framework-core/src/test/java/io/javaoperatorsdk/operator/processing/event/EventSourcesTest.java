package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EventSourcesTest {

  EventSources eventSources = new EventSources();

  @Test
  void cannotAddTwoEventSourcesWithSameName() {
    assertThrows(IllegalArgumentException.class, () -> {
      eventSources.add("name", mock(EventSource.class));
      eventSources.add("name", mock(EventSource.class));
    });
  }

}
