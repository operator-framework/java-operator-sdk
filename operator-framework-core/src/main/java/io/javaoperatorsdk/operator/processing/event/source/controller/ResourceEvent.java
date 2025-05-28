package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ResourceEvent extends Event {

  private final ResourceAction action;
  private final HasMetadata resource;

  public ResourceEvent(ResourceAction action, ResourceID resourceID, HasMetadata resource) {
    super(resourceID);
    this.action = action;
    this.resource = resource;
  }

  @Override
  public String toString() {
    return "ResourceEvent{"
        + "action="
        + action
        + ", associated resource id="
        + getRelatedCustomResourceID()
        + '}';
  }

  public ResourceAction getAction() {
    return action;
  }

  public Optional<HasMetadata> getResource() {
    return Optional.ofNullable(resource);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ResourceEvent that = (ResourceEvent) o;
    return action == that.action;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), action);
  }
}
