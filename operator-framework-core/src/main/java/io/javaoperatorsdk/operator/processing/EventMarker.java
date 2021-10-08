package io.javaoperatorsdk.operator.processing;

import java.util.HashMap;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

public class EventMarker {

  public enum EventingState {
    EVENT_PRESENT, ONLY_DELETE_EVENT_PRESENT, DELETE_AND_NON_DELETE_EVENT_PRESENT, NO_EVENT_PRESENT
  }

  private final HashMap<CustomResourceID, EventingState> eventingState = new HashMap<>();

  public EventingState getEventingState(CustomResourceID customResourceID) {
    EventingState actualState = eventingState.get(customResourceID);
    return actualState == null ? EventingState.NO_EVENT_PRESENT : actualState;
  }

  public void setEventingState(CustomResourceID customResourceID, EventingState state) {
    eventingState.put(customResourceID, state);
  }

  public void markEventReceived(Event event) {
    markEventReceived(event.getRelatedCustomResourceID());
  }

  public void markEventReceived(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    switch (actualState) {
      case ONLY_DELETE_EVENT_PRESENT:
        setEventingState(customResourceID,
            EventingState.DELETE_AND_NON_DELETE_EVENT_PRESENT);
        break;
      case NO_EVENT_PRESENT:
        setEventingState(customResourceID, EventingState.EVENT_PRESENT);
        break;
    }
  }

  public void unMarkEventReceived(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    switch (actualState) {
      case EVENT_PRESENT:
        setEventingState(customResourceID,
            EventingState.NO_EVENT_PRESENT);
        break;
      case DELETE_AND_NON_DELETE_EVENT_PRESENT:
        setEventingState(customResourceID,
            EventingState.ONLY_DELETE_EVENT_PRESENT);
        break;
    }
  }

  public void markDeleteEventReceived(Event event) {
    markDeleteEventReceived(event.getRelatedCustomResourceID());
  }

  public void markDeleteEventReceived(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    switch (actualState) {
      case NO_EVENT_PRESENT:
        setEventingState(customResourceID, EventingState.ONLY_DELETE_EVENT_PRESENT);
        break;
      case EVENT_PRESENT:
        setEventingState(customResourceID,
            EventingState.DELETE_AND_NON_DELETE_EVENT_PRESENT);
        break;
    }
  }

  public boolean isEventPresent(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    return actualState == EventingState.EVENT_PRESENT ||
        actualState == EventingState.DELETE_AND_NON_DELETE_EVENT_PRESENT;
  }

  public boolean isDeleteEventPresent(CustomResourceID customResourceID) {
    var actualState = getEventingState(customResourceID);
    return actualState == EventingState.DELETE_AND_NON_DELETE_EVENT_PRESENT ||
        actualState == EventingState.ONLY_DELETE_EVENT_PRESENT;
  }

  public void cleanup(CustomResourceID customResourceID) {
    eventingState.remove(customResourceID);
  }
}
