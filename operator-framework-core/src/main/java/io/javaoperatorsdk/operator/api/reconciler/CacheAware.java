package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

public interface CacheAware<P extends HasMetadata> {
  default <R> Optional<R> getSecondaryResource(Class<R> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <R> Set<R> getSecondaryResources(Class<R> expectedType);

  default <R> Stream<R> getSecondaryResourcesAsStream(Class<R> expectedType) {
    return getSecondaryResources(expectedType).stream();
  }

  <R> Optional<R> getSecondaryResource(Class<R> expectedType, String eventSourceName);

  ControllerConfiguration<P> getControllerConfiguration();

  /**
   * Retrieves the primary resource.
   *
   * @return the primary resource associated with the current reconciliation
   */
  P getPrimaryResource();

  /**
   * Retrieves the primary resource cache.
   *
   * @return the {@link IndexerResourceCache} associated with the associated {@link Reconciler} for
   *     this context
   */
  @SuppressWarnings("unused")
  IndexedResourceCache<P> getPrimaryCache();

  EventSourceRetriever<P> eventSourceRetriever();
}
