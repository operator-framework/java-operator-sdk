package io.javaoperatorsdk.operator.api.reconciler;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventSourceUtilsTest {

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void defaultNameDifferentForOtherInstance() {
    var eventSource1 = new PollingEventSource(HashMap::new, 1000, String.class);
    var eventSource2 = new PollingEventSource(HashMap::new, 1000, String.class);
    var eventSourceName1 = EventSourceUtils.generateNameFor(eventSource1);
    var eventSourceName2 = EventSourceUtils.generateNameFor(eventSource2);

    assertThat(eventSourceName1).isNotEqualTo(eventSourceName2);
  }

}
