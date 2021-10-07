package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.RetryInfo;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

public class ExecutionScope<R extends CustomResource<?, ?>> {

  // the latest custom resource from cache
  private final R customResource;
  private final RetryInfo retryInfo;

  public ExecutionScope(R customResource, RetryInfo retryInfo) {
    this.customResource = customResource;
    this.retryInfo = retryInfo;
  }

  public R getCustomResource() {
    return customResource;
  }

  public CustomResourceID getCustomResourceID() {
    return CustomResourceID.fromResource(customResource);
  }

  @Override
  public String toString() {
    return "ExecutionScope{"
        + ", customResource uid: "
        + customResource.getMetadata().getUid()
        + ", version: "
        + customResource.getMetadata().getResourceVersion()
        + '}';
  }

  public RetryInfo getRetryInfo() {
    return retryInfo;
  }
}
