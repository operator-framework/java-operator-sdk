package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EventListTest {

    @Test
    public void returnsLatestOfEventType() {
        TimerEvent event2 = new TimerEvent("1", null);
        EventList eventList = new EventList(Arrays.asList(
                mock(Event.class),
                new TimerEvent("2", null),
                event2,
                mock(Event.class)));

        assertThat(eventList.getLatestOfType(TimerEvent.class).get()).isSameInstanceAs(event2);
    }
}