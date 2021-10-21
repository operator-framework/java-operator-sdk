package io.javaoperatorsdk.operator.processing.event;

@SuppressWarnings("rawtypes")
public class DefaultEvent implements Event {

  private final CustomResourceID relatedCustomResource;
  private final Type type;

  public DefaultEvent(CustomResourceID targetCustomResource,
      Type type) {
    this.relatedCustomResource = targetCustomResource;
    this.type = type;
  }

  @Override
  public CustomResourceID getRelatedCustomResourceID() {
    return relatedCustomResource;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String toString() {
    return "DefaultEvent{" +
        "relatedCustomResource=" + relatedCustomResource +
        ", type=" + type +
        '}';
  }
}
