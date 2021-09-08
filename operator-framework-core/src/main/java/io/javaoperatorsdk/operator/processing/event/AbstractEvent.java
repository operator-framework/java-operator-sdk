package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.function.Predicate;

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
