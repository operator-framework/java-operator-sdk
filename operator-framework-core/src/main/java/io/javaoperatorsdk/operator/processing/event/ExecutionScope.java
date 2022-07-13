package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

class ExecutionScope<R extends HasMetadata> {

  // the latest custom resource from cache
  private final R resource;
  private final RetryInfo retryInfo;

  ExecutionScope(R resource, RetryInfo retryInfo) {
    this.resource = resource;
    this.retryInfo = retryInfo;
  }

  public R getResource() {
    return resource;
  }

  public ResourceID getResourceID() {
    return ResourceID.fromResource(resource);
  }

  @Override
  public String toString() {
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
