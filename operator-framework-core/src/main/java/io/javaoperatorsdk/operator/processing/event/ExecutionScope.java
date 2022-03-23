package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

class ExecutionScope<R extends HasMetadata> {

  // the latest custom resource from cache
  private final R resource;
  private final RetryInfo retryInfo;

  public ExecutionScope(R resource, RetryInfo retryInfo) {
    this.resource = resource;
    this.retryInfo = retryInfo;
  }

  public R getResource() {
    return resource;
  }

  public ObjectKey getCustomResourceID() {
    return ObjectKey.fromResource(resource);
  }

  @Override
  public String toString() {
    return "ExecutionScope{"
        + " resource id: "
        + ObjectKey.fromResource(resource)
        + ", version: "
        + resource.getMetadata().getResourceVersion()
        + '}';
  }

  public RetryInfo getRetryInfo() {
    return retryInfo;
  }
}
