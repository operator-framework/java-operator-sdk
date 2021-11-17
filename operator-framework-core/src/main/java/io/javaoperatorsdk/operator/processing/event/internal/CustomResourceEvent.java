package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class CustomResourceEvent extends Event {

  private final ResourceAction action;

  public CustomResourceEvent(ResourceAction action,
      ResourceID resourceID) {
    super(resourceID);
    this.action = action;
  }

  @Override
  public String toString() {
    return "CustomResourceEvent{" +
        "action=" + action +
        '}';
  }

  public ResourceAction getAction() {
    return action;
  }

}
