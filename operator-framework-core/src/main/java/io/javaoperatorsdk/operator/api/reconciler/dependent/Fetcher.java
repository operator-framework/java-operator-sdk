package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

@FunctionalInterface
public interface Fetcher<R extends HasMetadata> {
  Fetcher<? extends HasMetadata> DEFAULT =
      (owner, cache) -> cache.get(ResourceID.fromResource(owner)).orElse(null);

  @SuppressWarnings("unchecked")
  static <T extends HasMetadata> Fetcher<T> defaultFetcher() {
    return (Fetcher<T>) DEFAULT;
  }

  R fetchFor(HasMetadata owner, ResourceCache<R> cache);

}
