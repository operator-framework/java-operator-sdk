package io.javaoperatorsdk.operator.processing.event;

import java.util.function.Predicate;

import io.fabric8.kubernetes.client.CustomResource;

/**
 * @deprecated use {@link DefaultEvent} instead
 */
@Deprecated
public class AbstractEvent extends DefaultEvent {

  public AbstractEvent(String relatedCustomResourceUid, EventSource eventSource) {
    super(relatedCustomResourceUid, eventSource);
  }

  public AbstractEvent(
      Predicate<CustomResource> customResourcesSelector, EventSource eventSource) {
    super(customResourcesSelector, eventSource);
  }
}
