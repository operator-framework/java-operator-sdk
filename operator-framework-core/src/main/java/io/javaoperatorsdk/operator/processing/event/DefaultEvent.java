package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.function.Predicate;

@SuppressWarnings("rawtypes")
public class DefaultEvent implements Event {

  private final String relatedCustomResourceUid;
  private final Predicate<CustomResource> customResourcesSelector;
  private final EventSource eventSource;

  public DefaultEvent(String relatedCustomResourceUid, EventSource eventSource) {
    this.relatedCustomResourceUid = relatedCustomResourceUid;
    this.customResourcesSelector = null;
    this.eventSource = eventSource;
  }

  public DefaultEvent(Predicate<CustomResource> customResourcesSelector, EventSource eventSource) {
    this.relatedCustomResourceUid = null;
    this.customResourcesSelector = customResourcesSelector;
    this.eventSource = eventSource;
  }

  @Override
  public String getRelatedCustomResourceUid() {
    return relatedCustomResourceUid;
  }

  public Predicate<CustomResource> getCustomResourcesSelector() {
    return customResourcesSelector;
  }

  @Override
  public EventSource getEventSource() {
    return eventSource;
  }

  @Override
  public String toString() {
    return "{ class="
        + this.getClass().getName()
        + ", relatedCustomResourceUid="
        + relatedCustomResourceUid
        + ", customResourcesSelector="
        + customResourcesSelector
        + ", eventSource="
        + eventSource
        + " }";
  }
}
