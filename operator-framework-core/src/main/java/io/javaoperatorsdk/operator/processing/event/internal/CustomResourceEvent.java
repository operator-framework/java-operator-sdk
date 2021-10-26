package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

public class CustomResourceEvent extends Event {

  private final ResourceAction action;

  public CustomResourceEvent(ResourceAction action,
      CustomResourceID customResourceID) {
    super(customResourceID);
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
