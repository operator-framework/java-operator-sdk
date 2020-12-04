package io.javaoperatorsdk.operator.processing;

import java.util.List;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Event;

public class ExecutionScope {

  private List<Event> events;
  // the latest custom resource from cache
  private CustomResource customResource;
  private RetryInfo retryInfo;

  public ExecutionScope(List<Event> list, CustomResource customResource, RetryInfo retryInfo) {
    this.events = list;
    this.customResource = customResource;
    this.retryInfo = retryInfo;
  }

  public List<Event> getEvents() {
    return events;
  }

  public CustomResource getCustomResource() {
    return customResource;
  }

  public String getCustomResourceUid() {
    return customResource.getMetadata().getUid();
  }

  @Override
  public String toString() {
    return "ExecutionScope{"
        + "events="
        + events
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
