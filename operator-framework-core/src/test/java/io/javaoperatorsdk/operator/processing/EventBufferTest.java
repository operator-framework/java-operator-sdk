package io.javaoperatorsdk.operator.processing;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEvent;

import static org.assertj.core.api.Assertions.assertThat;

class EventBufferTest {

  private EventBuffer eventBuffer = new EventBuffer();

  String name = UUID.randomUUID().toString();
  CustomResourceID customResourceID = new CustomResourceID(name);
  Event testEvent1 = new TimerEvent(customResourceID);
  Event testEvent2 = new TimerEvent(customResourceID);

  @Test
  public void storesEvents() {
    eventBuffer.addEvent(testEvent1);
    eventBuffer.addEvent(testEvent2);

    assertThat(eventBuffer.containsEvents(testEvent1.getRelatedCustomResourceID())).isTrue();
    List<Event> events = eventBuffer.getAndRemoveEventsForExecution(customResourceID);
    assertThat(events).hasSize(2);
  }

  @Test
  public void getsAndRemovesEvents() {
    eventBuffer.addEvent(testEvent1);
    eventBuffer.addEvent(testEvent2);

    List<Event> events = eventBuffer.getAndRemoveEventsForExecution(new CustomResourceID(name));
    assertThat(events).hasSize(2);
    assertThat(events).contains(testEvent1, testEvent2);
  }

  @Test
  public void checksIfThereAreStoredEvents() {
    eventBuffer.addEvent(testEvent1);
    eventBuffer.addEvent(testEvent2);

    assertThat(eventBuffer.containsEvents(testEvent1.getRelatedCustomResourceID())).isTrue();
  }

  @Test
  public void canClearEvents() {
    eventBuffer.addEvent(testEvent1);
    eventBuffer.addEvent(testEvent2);

    eventBuffer.cleanup(customResourceID);

    assertThat(eventBuffer.containsEvents(testEvent1.getRelatedCustomResourceID())).isFalse();
  }
}
