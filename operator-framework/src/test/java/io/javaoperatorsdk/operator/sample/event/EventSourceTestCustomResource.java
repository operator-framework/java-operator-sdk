package io.javaoperatorsdk.operator.sample.event;

import io.fabric8.kubernetes.client.CustomResource;

public class EventSourceTestCustomResource extends CustomResource {

  private EventSourceTestCustomResourceSpec spec;

  private EventSourceTestCustomResourceStatus status;

  public EventSourceTestCustomResourceSpec getSpec() {
    return spec;
  }

  public void setSpec(EventSourceTestCustomResourceSpec spec) {
    this.spec = spec;
  }

  public EventSourceTestCustomResourceStatus getStatus() {
    return status;
  }

  public void setStatus(EventSourceTestCustomResourceStatus status) {
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
