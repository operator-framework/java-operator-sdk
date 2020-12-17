package io.javaoperatorsdk.operator.doubleupdate;

import io.fabric8.kubernetes.client.CustomResource;

public class DoubleUpdateTestCustomResource extends CustomResource {

  private DoubleUpdateTestCustomResourceSpec spec;

  private DoubleUpdateTestCustomResourceStatus status;

  public DoubleUpdateTestCustomResourceSpec getSpec() {
    return spec;
  }

  public void setSpec(DoubleUpdateTestCustomResourceSpec spec) {
    this.spec = spec;
  }

  public DoubleUpdateTestCustomResourceStatus getStatus() {
    return status;
  }

  public void setStatus(DoubleUpdateTestCustomResourceStatus status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return "DoubleUpdateTestCustomResource{"
        + "spec="
        + spec
        + ", status="
        + status
        + ", extendedFrom="
        + super.toString()
        + '}';
  }
}
