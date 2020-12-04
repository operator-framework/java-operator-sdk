package io.javaoperatorsdk.operator.processing.event;

import java.util.Arrays;

import io.javaoperatorsdk.operator.api.Event;
import io.javaoperatorsdk.operator.api.EventList;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEvent;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
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

    assertThat(eventList.getLatestOfType(TimerEvent.class).get()).isSameInstanceAs(event2);
  }
}
