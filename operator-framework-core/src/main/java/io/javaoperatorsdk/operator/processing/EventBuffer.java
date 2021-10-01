package io.javaoperatorsdk.operator.processing;

import java.util.*;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

class EventBuffer {

  private final Map<CustomResourceID, List<Event>> events = new HashMap<>();

  /** @deprecated use {@link #addEvent(CustomResourceID, Event)} */
  @Deprecated
  public void addEvent(Event event) {
    addEvent(event.getRelatedCustomResourceID(), event);
  }

  public void addEvent(CustomResourceID uid, Event event) {
    Objects.requireNonNull(uid, "uid");
    Objects.requireNonNull(event, "event");

    List<Event> crEvents = events.computeIfAbsent(uid, (customResourceID) -> new LinkedList<>());
    crEvents.add(event);
  }

  public boolean newEventsExists(CustomResourceID resourceId) {
    return events.get(resourceId) != null && !events.get(resourceId).isEmpty();
  }

  public void putBackEvents(CustomResourceID resourceUid, List<Event> oldEvents) {
    List<Event> crEvents = events.computeIfAbsent(resourceUid, (id) -> new LinkedList<>());
    crEvents.addAll(0, oldEvents);
  }

  public boolean containsEvents(CustomResourceID customResourceId) {
    return events.get(customResourceId) != null;
  }

  public List<Event> getAndRemoveEventsForExecution(CustomResourceID resourceUid) {
    List<Event> crEvents = events.remove(resourceUid);
    return crEvents == null ? Collections.emptyList() : crEvents;
  }

  public void cleanup(CustomResourceID resourceUid) {
    events.remove(resourceUid);
  }
}
