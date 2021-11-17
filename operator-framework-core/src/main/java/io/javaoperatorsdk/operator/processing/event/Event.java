package io.javaoperatorsdk.operator.processing.event;

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
    return "DefaultEvent{" +
        "relatedCustomResource=" + relatedCustomResource +
        '}';
  }
}
