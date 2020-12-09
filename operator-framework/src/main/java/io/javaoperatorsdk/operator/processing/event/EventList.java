package io.javaoperatorsdk.operator.processing.event;

import java.util.List;
import java.util.Optional;

public class EventList {

  private final List<Event> eventList;

  public EventList(List<Event> eventList) {
    this.eventList = eventList;
  }

  public List<Event> getList() {
    return eventList;
  }

  public <T extends Event> Optional<T> getLatestOfType(Class<T> eventType) {
    for (int i = eventList.size() - 1; i >= 0; i--) {
      Event event = eventList.get(i);
      if (event.getClass().isAssignableFrom(eventType)) {
        return (Optional<T>) Optional.of(event);
      }
    }
    return Optional.empty();
  }
}
