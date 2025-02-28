package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

public interface EventSourceReferencer<P extends HasMetadata> {

  default void useEventSourceWithName(String name) {}

  /**
   * Throws {@link EventSourceNotFoundException} an exception if the target event source to use is
   * not found.
   *
   * @param eventSourceRetriever for event sources
   */
  void resolveEventSource(EventSourceRetriever<P> eventSourceRetriever)
      throws EventSourceNotFoundException;
}
