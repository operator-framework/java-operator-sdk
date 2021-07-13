package io.javaoperatorsdk.operator.processing;

import io.javaoperatorsdk.operator.processing.event.Event;
import java.util.*;

class EventBuffer {

  private final Map<String, List<Event>> events = new HashMap<>();

  /** @deprecated use {@link #addEvent(String, Event)} */
  @Deprecated
  public void addEvent(Event event) {
    addEvent(event.getRelatedCustomResourceUid(), event);
  }

  public void addEvent(String uid, Event event) {
    Objects.requireNonNull(uid, "uid");
    Objects.requireNonNull(event, "event");

    List<Event> crEvents = events.computeIfAbsent(uid, (id) -> new LinkedList<>());
    crEvents.add(event);
  }

  public boolean newEventsExists(String resourceId) {
    return events.get(resourceId) != null && !events.get(resourceId).isEmpty();
  }

  public void putBackEvents(String resourceUid, List<Event> oldEvents) {
    List<Event> crEvents = events.computeIfAbsent(resourceUid, (id) -> new LinkedList<>());
    crEvents.addAll(0, oldEvents);
  }

  public boolean containsEvents(String customResourceId) {
    return events.get(customResourceId) != null;
  }

  public List<Event> getAndRemoveEventsForExecution(String resourceUid) {
    List<Event> crEvents = events.remove(resourceUid);
    return crEvents == null ? Collections.emptyList() : crEvents;
  }

  public void cleanup(String resourceUid) {
    events.remove(resourceUid);
  }
}
