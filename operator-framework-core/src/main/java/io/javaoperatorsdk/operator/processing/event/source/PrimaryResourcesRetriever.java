package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@FunctionalInterface
public interface PrimaryResourcesRetriever<T extends HasMetadata, P extends HasMetadata> {
  Set<ResourceID> associatedPrimaryResources(T dependentResource, EventSourceRegistry<P> registry);
}
