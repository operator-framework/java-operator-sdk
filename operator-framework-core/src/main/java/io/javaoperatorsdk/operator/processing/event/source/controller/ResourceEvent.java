package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public class ResourceEvent extends Event {

  private final ResourceAction action;

  public ResourceEvent(ResourceAction action,
      ObjectKey objectKey) {
    super(objectKey);
    this.action = action;
  }

  @Override
  public String toString() {
    return "ResourceEvent{" +
        "action=" + action +
        ", associated resource id=" + getRelatedCustomResourceID() +
        '}';
  }

  public ResourceAction getAction() {
    return action;
  }

}
