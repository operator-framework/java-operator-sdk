package io.javaoperatorsdk.operator.processing.event;

import java.util.Objects;

public class Event {

  private final ResourceID relatedCustomResource;

  public Event(ResourceID targetCustomResource) {
    this.relatedCustomResource = targetCustomResource;
  }

  public ResourceID getRelatedCustomResourceID() {
    return relatedCustomResource;
  }

  @Override
  public String toString() {
    return "Event{" + "relatedCustomResource=" + relatedCustomResource + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Event event = (Event) o;
    return Objects.equals(relatedCustomResource, event.relatedCustomResource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(relatedCustomResource);
  }
}
