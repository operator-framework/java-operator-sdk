package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;

@FunctionalInterface
public interface AssociatedSecondaryRetriever<T extends HasMetadata, P extends HasMetadata> {
  T associatedSecondary(P primary, EventSourceRegistry<P> registry);
}
