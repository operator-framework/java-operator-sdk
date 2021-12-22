package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

/**
 * Contextual information made available to prepare event sources.
 *
 * @param <P> the type associated with the primary resource that is handled by your reconciler
 */
public class EventSourceInitializationContext<P extends HasMetadata> {

  private final ResourceCache<P> primaryCache;

  public EventSourceInitializationContext(ResourceCache<P> primaryCache) {
    this.primaryCache = primaryCache;
  }

  /**
   * Retrieves the cache that an {@link EventSource} can query to retrieve primary resources
   *
   * @return the primary resource cache
   */
  public ResourceCache<P> getPrimaryCache() {
    return primaryCache;
  }
}
