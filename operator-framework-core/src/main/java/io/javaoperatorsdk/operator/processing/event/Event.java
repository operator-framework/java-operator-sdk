package io.javaoperatorsdk.operator.processing.event;

public interface Event {

  CustomResourceID getRelatedCustomResourceID();

  Type getType();

  default boolean isDeleteEvent() {
    return Type.DELETED == getType();
  }

  enum Type {
    ADDED, UPDATED, DELETED, OTHER, UNKNOWN
  }
}
