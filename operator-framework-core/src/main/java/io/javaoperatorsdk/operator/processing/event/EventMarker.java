package io.javaoperatorsdk.operator.processing.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.javaoperatorsdk.operator.processing.event.EventMarker.EventingState.NO_EVENT_PRESENT;

/**
 * Manages the state of received events. Basically there can be only three distinct states relevant
 * for event processing. Either an event is received, so we eventually process or no event for
 * processing at the moment. The third case is if a DELETE event is received, this is a special case
 * meaning that the custom resource is deleted. We don't want to do any processing anymore so other
 * events are irrelevant for us from this point. Note that the dependant resources are either
 * cleaned up by K8S garbage collection or by the controller implementation for cleanup.
 */
class EventMarker {

  public enum EventingState {
    /** Event but NOT Delete event present */
    EVENT_PRESENT, NO_EVENT_PRESENT,
    /** Delete event present, from this point other events are not relevant */
    DELETE_EVENT_PRESENT,
  }

  private final HashMap<ObjectKey, EventingState> eventingState = new HashMap<>();

  private EventingState getEventingState(ObjectKey objectKey) {
    EventingState actualState = eventingState.get(objectKey);
    return actualState == null ? NO_EVENT_PRESENT : actualState;
  }

  private void setEventingState(ObjectKey objectKey, EventingState state) {
    eventingState.put(objectKey, state);
  }

  public void markEventReceived(Event event) {
    markEventReceived(event.getRelatedCustomResourceID());
  }

  public void markEventReceived(ObjectKey objectKey) {
    if (deleteEventPresent(objectKey)) {
      throw new IllegalStateException("Cannot receive event after a delete event received");
    }
    setEventingState(objectKey, EventingState.EVENT_PRESENT);
  }

  public void unMarkEventReceived(ObjectKey objectKey) {
    var actualState = getEventingState(objectKey);
    switch (actualState) {
      case EVENT_PRESENT:
        setEventingState(objectKey,
            NO_EVENT_PRESENT);
        break;
      case DELETE_EVENT_PRESENT:
        throw new IllegalStateException("Cannot unmark delete event.");
    }
  }

  public void markDeleteEventReceived(Event event) {
    markDeleteEventReceived(event.getRelatedCustomResourceID());
  }

  public void markDeleteEventReceived(ObjectKey objectKey) {
    setEventingState(objectKey, EventingState.DELETE_EVENT_PRESENT);
  }

  public boolean deleteEventPresent(ObjectKey objectKey) {
    return getEventingState(objectKey) == EventingState.DELETE_EVENT_PRESENT;
  }

  public boolean eventPresent(ObjectKey objectKey) {
    var actualState = getEventingState(objectKey);
    return actualState == EventingState.EVENT_PRESENT;
  }

  public boolean noEventPresent(ObjectKey objectKey) {
    var actualState = getEventingState(objectKey);
    return actualState == NO_EVENT_PRESENT;
  }

  public void cleanup(ObjectKey objectKey) {
    eventingState.remove(objectKey);
  }

  public List<ObjectKey> resourceIDsWithEventPresent() {
    return eventingState.entrySet().stream()
        .filter(e -> e.getValue() != NO_EVENT_PRESENT)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

}
