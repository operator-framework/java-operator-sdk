package io.javaoperatorsdk.operator.processing;

import java.util.HashSet;
import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

public class EventMarker {

  private final Set<CustomResourceID> marker = new HashSet<>();
  private final Set<CustomResourceID> deleteEventMarker = new HashSet<>();

  public void markEventReceived(Event event) {
    markEventReceived(event.getRelatedCustomResourceID());
  }

  public void markEventReceived(CustomResourceID customResourceID) {
    marker.add(customResourceID);
  }

  public boolean isEventReceived(Event event) {
    return isEventReceived(event.getRelatedCustomResourceID());
  }

  public boolean isEventReceived(CustomResourceID customResourceID) {
    return marker.contains(customResourceID);
  }

  public boolean unmarkEvent(CustomResourceID customResourceID) {
    return marker.remove(customResourceID);
  }

  public void markDeleteEventReceived(CustomResourceID customResourceID) {
    deleteEventMarker.add(customResourceID);
  }

  public boolean isDeleteEventReceived(Event event) {
    return isDeleteEventReceived(event.getRelatedCustomResourceID());
  }

  public boolean isDeleteEventReceived(CustomResourceID customResourceID) {
    return deleteEventMarker.contains(customResourceID);
  }

  public boolean unmarkDeleteReceived(CustomResourceID customResourceID) {
    return deleteEventMarker.remove(customResourceID);
  }

}
