package io.javaoperatorsdk.operator.processing.event;

@SuppressWarnings("rawtypes")
public class Event {

  private final CustomResourceID relatedCustomResource;

  public Event(CustomResourceID targetCustomResource) {
    this.relatedCustomResource = targetCustomResource;
  }

  public CustomResourceID getRelatedCustomResourceID() {
    return relatedCustomResource;
  }

  @Override
  public String toString() {
    return "DefaultEvent{" +
        "relatedCustomResource=" + relatedCustomResource +
        '}';
  }
}
