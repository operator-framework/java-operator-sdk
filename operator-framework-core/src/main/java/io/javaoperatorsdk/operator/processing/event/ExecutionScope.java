package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

class ExecutionScope<R extends HasMetadata> {

  // the latest custom resource from cache
  private R resource;
  private final RetryInfo retryInfo;
  private boolean deleteEvent;
  private boolean isDeleteFinalStateUnknown;

  ExecutionScope(
      R resource, RetryInfo retryInfo, boolean deleteEvent, boolean isDeleteFinalStateUnknown) {
    this.retryInfo = retryInfo;
    this.deleteEvent = deleteEvent;
    this.isDeleteFinalStateUnknown = isDeleteFinalStateUnknown;
    this.resource = resource;
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
    return "ExecutionScope{"
        + "resource="
        + ResourceID.fromResource(resource)
        + ", retryInfo="
        + retryInfo
        + ", deleteEvent="
        + deleteEvent
        + ", isDeleteFinalStateUnknown="
        + isDeleteFinalStateUnknown
        + '}';
  }

  public RetryInfo getRetryInfo() {
    return retryInfo;
  }
}
