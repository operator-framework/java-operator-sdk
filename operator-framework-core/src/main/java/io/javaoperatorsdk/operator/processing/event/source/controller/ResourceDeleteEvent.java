package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Extends ResourceEvent for informer Delete events, it holds also information if the final stat is
 * unknown for the deleted resource.
 */
public class ResourceDeleteEvent extends ResourceEvent {

  private final boolean deletedFinalStateUnknown;

  public ResourceDeleteEvent(
      ResourceAction action,
      ResourceID resourceID,
      HasMetadata resource,
      boolean deletedFinalStateUnknown) {
    super(action, resourceID, resource);
    this.deletedFinalStateUnknown = deletedFinalStateUnknown;
  }

  public boolean isDeletedFinalStateUnknown() {
    return deletedFinalStateUnknown;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ResourceDeleteEvent that = (ResourceDeleteEvent) o;
    return deletedFinalStateUnknown == that.deletedFinalStateUnknown;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), deletedFinalStateUnknown);
  }
}
