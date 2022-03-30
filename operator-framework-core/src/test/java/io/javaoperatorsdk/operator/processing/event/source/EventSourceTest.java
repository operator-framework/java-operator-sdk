package io.javaoperatorsdk.operator.processing.event.source;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class EventSourceTest {

  @Test
  void defaultNameDifferentForOtherInstance() {
    var eventSource1 = new PollingEventSource(() -> new HashMap<>(), 1000, String.class);
    var eventSource2 = new PollingEventSource(() -> new HashMap<>(), 1000, String.class);
    var eventSourceName1 = EventSource.defaultNameFor(eventSource1);
    var eventSourceName2 = EventSource.defaultNameFor(eventSource2);

    assertThat(eventSourceName1).isNotEqualTo(eventSourceName2);
  }
}
