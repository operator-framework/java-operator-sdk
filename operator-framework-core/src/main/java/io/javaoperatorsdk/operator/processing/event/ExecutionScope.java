package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

class ExecutionScope<R extends HasMetadata> {

  // the latest custom resource from cache
  private R resource;
  private final RetryInfo retryInfo;
  private boolean deleteEvent;
  private boolean isDeleteFinalStateUnknown;

  ExecutionScope(RetryInfo retryInfo, boolean deleteEvent, boolean isDeleteFinalStateUnknown) {
    this.retryInfo = retryInfo;
    this.deleteEvent = deleteEvent;
    this.isDeleteFinalStateUnknown = isDeleteFinalStateUnknown;
  }

  public ExecutionScope<R> setResource(R resource) {
    this.resource = resource;
    return this;
  }

  public R getResource() {
    return resource;
  }

  public ResourceID getResourceID() {
    return ResourceID.fromResource(resource);
  }

  public boolean isDeleteEvent() {
    return deleteEvent;
  }

  public void setDeleteEvent(boolean deleteEvent) {
    this.deleteEvent = deleteEvent;
  }

  public boolean isDeleteFinalStateUnknown() {
    return isDeleteFinalStateUnknown;
  }

  public void setDeleteFinalStateUnknown(boolean deleteFinalStateUnknown) {
    isDeleteFinalStateUnknown = deleteFinalStateUnknown;
  }

  @Override
  public String toString() {
    if (resource == null) {
      return "ExecutionScope{resource: null}";
    } else
      return "ExecutionScope{"
          + " resource id: "
          + ResourceID.fromResource(resource)
          + ", version: "
          + resource.getMetadata().getResourceVersion()
          + '}';
  }

  public RetryInfo getRetryInfo() {
    return retryInfo;
  }
}
