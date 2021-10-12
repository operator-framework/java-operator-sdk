package io.javaoperatorsdk.operator.processing.event;

@SuppressWarnings("rawtypes")
public class DefaultEvent implements Event {
  private final CustomResourceID relatedCustomResource;


  public DefaultEvent(CustomResourceID targetCustomResource) {
    this.relatedCustomResource = targetCustomResource;
  }


  @Override
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
