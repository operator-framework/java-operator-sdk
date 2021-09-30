package io.javaoperatorsdk.operator.processing.event;

import java.util.function.Predicate;

import io.fabric8.kubernetes.client.CustomResource;

public interface Event {

  /**
   * @return the UID of the the {@link CustomResource} for which a reconcile loop should be
   *         triggered.
   * @deprecated use {@link #getCustomResourcesSelector()}
   */
  @Deprecated
  String getRelatedCustomResourceUid();

  /**
   * The selector used to determine the {@link CustomResource} for which a reconcile loop should be
   * triggered.
   * 
   * @return predicate used to match the target CustomResource
   */
  Predicate<CustomResource> getCustomResourcesSelector();

  /** @return the {@link EventSource} that has generated the event. */
  EventSource getEventSource();
}
