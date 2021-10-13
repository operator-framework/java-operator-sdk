package io.javaoperatorsdk.operator.processing;

import java.util.HashMap;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

/**
 * Manages the state of received events. Basically there can be only three distinct states relevant
 * for event processing. Either an event is received, so we eventually process or no event for
 * processing at the moment. The third case is if a DELETE event is received, this is a special case
 * meaning that the custom resource is deleted. We don't want to do any processing anymore is other
 * events are irrelevant for us from this point. Note that the dependant resources are either
 * cleaned up by K8S garbage collection or by the controller implementation for cleanup.
 */
public class EventMarker {

  public enum EventingState {
    /** Event but NOT Delete event present */
    EVENT_PRESENT, NO_EVENT_PRESENT,
    /** Delete event present, from this point other events are not relevant */
    DELETE_EVENT_PRESENT,
  }

  private final HashMap<CustomResourceID, EventingState> eventingState = new HashMap<>();

  private EventingState getEventingState(CustomResourceID customResourceID) {
    EventingState actualState = eventingState.get(customResourceID);
    return actualState == null ? EventingState.NO_EVENT_PRESENT : actualState;
  }

  private void setEventingState(CustomResourceID customResourceID, EventingState state) {
    eventingState.put(customResourceID, state);
  }

  public void markEventReceived(Event event) {
    markEventReceived(event.getRelatedCustomResourceID());
  }

  public void markEventReceived(CustomResourceID customResourceID) {
    if (deleteEventPresent(customResourceID)) {
      throw new IllegalStateException("Cannot receive event after a delete event received");
    }
    setEventingState(customResourceID, EventingState.EVENT_PRESENT);
  }

  public void unMarkEventReceived(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    switch (actualState) {
      case EVENT_PRESENT:
        setEventingState(customResourceID,
            EventingState.NO_EVENT_PRESENT);
        break;
      case DELETE_EVENT_PRESENT:
        throw new IllegalStateException("Cannot unmark delete event.");
    }
  }

  public void markDeleteEventReceived(Event event) {
    markDeleteEventReceived(event.getRelatedCustomResourceID());
  }

  public void markDeleteEventReceived(CustomResourceID customResourceID) {
    setEventingState(customResourceID, EventingState.DELETE_EVENT_PRESENT);
  }

  public boolean deleteEventPresent(CustomResourceID customResourceID) {
    return getEventingState(customResourceID) == EventingState.DELETE_EVENT_PRESENT;
  }

  public boolean eventPresent(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    return actualState == EventingState.EVENT_PRESENT;
  }

  public boolean noEventPresent(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    return actualState == EventingState.NO_EVENT_PRESENT;
  }

  public void cleanup(CustomResourceID customResourceID) {
    eventingState.remove(customResourceID);
  }
}
