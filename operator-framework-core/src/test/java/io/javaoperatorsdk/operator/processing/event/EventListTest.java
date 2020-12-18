package io.javaoperatorsdk.operator.processing.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.javaoperatorsdk.operator.processing.event.internal.TimerEvent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EventListTest {

  @Test
  public void returnsLatestOfEventType() {
    TimerEvent event2 = new TimerEvent("1", null);
    EventList eventList =
        new EventList(
            Arrays.asList(mock(Event.class), new TimerEvent("2", null), event2, mock(Event.class)));

    assertThat(eventList.getLatestOfType(TimerEvent.class).get()).isEqualTo(event2);
  }
}
