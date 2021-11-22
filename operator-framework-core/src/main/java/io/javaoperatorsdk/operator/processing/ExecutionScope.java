package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ExecutionScope<R extends HasMetadata> {

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

  public ResourceID getCustomResourceID() {
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
