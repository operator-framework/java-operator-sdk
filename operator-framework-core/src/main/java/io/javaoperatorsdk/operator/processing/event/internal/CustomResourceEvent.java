package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;

public class CustomResourceEvent extends DefaultEvent {

  private final ResourceAction action;
  private final CustomResource customResource;


  public CustomResourceEvent(ResourceAction action,
      CustomResource resource) {
    super(CustomResourceID.fromResource(resource));
    this.customResource = resource;
    this.action = action;
  }

  @Override
  public String toString() {
    return "CustomResourceEvent{" +
        "action=" + action +
        ", customResource=" + customResource +
        '}';
  }

  public CustomResource getCustomResource() {
    return customResource;
  }

  public ResourceAction getAction() {
    return action;
  }

}
