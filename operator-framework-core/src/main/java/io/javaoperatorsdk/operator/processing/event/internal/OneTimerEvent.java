package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.processing.event.AbstractEvent;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventSource;
import java.util.Collections;
import java.util.List;

public class OneTimerEvent extends AbstractEvent {

  private final List<Event> events;

  public OneTimerEvent(
      String relatedCustomResourceUid, EventSource eventSource, List<Event> events) {
    super(relatedCustomResourceUid, eventSource);
    this.events = events;
  }

  public List<Event> getEvents() {
    if (events == null) {
      return Collections.EMPTY_LIST;
    }
    return events;
  }
}
