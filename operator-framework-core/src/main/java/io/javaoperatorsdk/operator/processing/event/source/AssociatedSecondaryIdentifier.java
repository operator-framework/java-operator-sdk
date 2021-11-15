package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@FunctionalInterface
public interface AssociatedSecondaryIdentifier<P extends HasMetadata> {
  ResourceID associatedSecondaryID(P primary, EventSourceRegistry<P> registry);
}
