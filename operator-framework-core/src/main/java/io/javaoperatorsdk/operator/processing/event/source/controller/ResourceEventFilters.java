package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;

/**
 * Convenience implementations of, and utility methods for, {@link ResourceEventFilter}.
 */
@Deprecated
public final class ResourceEventFilters {

  private static final ResourceEventFilter<HasMetadata> PASSTHROUGH =
      (configuration, oldResource, newResource) -> true;

  private ResourceEventFilters() {}

  /**
   * Retrieves a filter that accepts all events.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter that accepts all events
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> ResourceEventFilter<T> passthrough() {
    return (ResourceEventFilter<T>) PASSTHROUGH;
  }

}
