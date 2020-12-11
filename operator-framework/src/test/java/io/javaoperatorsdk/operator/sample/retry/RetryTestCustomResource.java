package io.javaoperatorsdk.operator.sample.retry;

import io.fabric8.kubernetes.client.CustomResource;

public class RetryTestCustomResource extends CustomResource {

  private RetryTestCustomResourceSpec spec;

  private RetryTestCustomResourceStatus status;

  public RetryTestCustomResourceSpec getSpec() {
    return spec;
  }

  public void setSpec(RetryTestCustomResourceSpec spec) {
    this.spec = spec;
  }

  public RetryTestCustomResourceStatus getStatus() {
    return status;
  }

  public void setStatus(RetryTestCustomResourceStatus status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return "TestCustomResource{"
        + "spec="
        + spec
        + ", status="
        + status
        + ", extendedFrom="
        + super.toString()
        + '}';
  }
}
