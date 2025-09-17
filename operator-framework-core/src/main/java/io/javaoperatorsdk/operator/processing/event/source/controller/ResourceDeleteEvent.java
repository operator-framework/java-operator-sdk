package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

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
}
