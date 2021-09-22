package io.javaoperatorsdk.operator.processing.event;

import java.util.Objects;
import java.util.function.Predicate;

import io.fabric8.kubernetes.client.CustomResource;

@SuppressWarnings("rawtypes")
public class DefaultEvent implements Event {
  private final Predicate<CustomResource> customResourcesSelector;
  private final EventSource eventSource;

  public DefaultEvent(String relatedCustomResourceUid, EventSource eventSource) {
    this.customResourcesSelector = new UIDMatchingPredicate(relatedCustomResourceUid);
    this.eventSource = eventSource;
  }

  public DefaultEvent(Predicate<CustomResource> customResourcesSelector, EventSource eventSource) {
    this.customResourcesSelector = customResourcesSelector;
    this.eventSource = eventSource;
  }

  @Override
  public String getRelatedCustomResourceUid() {
    if (customResourcesSelector instanceof UIDMatchingPredicate) {
      UIDMatchingPredicate resourcesSelector = (UIDMatchingPredicate) customResourcesSelector;
      return resourcesSelector.uid;
    } else {
      return null;
    }
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
        + ", customResourcesSelector="
        + customResourcesSelector
        + ", eventSource="
        + eventSource
        + " }";
  }

  public static class UIDMatchingPredicate implements Predicate<CustomResource> {
    private final String uid;

    public UIDMatchingPredicate(String uid) {
      this.uid = uid;
    }

    @Override
    public boolean test(CustomResource customResource) {
      return Objects.equals(uid, customResource.getMetadata().getUid());
    }

    @Override
    public String toString() {
      return "UIDMatchingPredicate{uid='" + uid + "'}";
    }
  }
}
