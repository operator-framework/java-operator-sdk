package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface TemporalPrimaryToSecondaryIndex<R extends HasMetadata> {

  void explicitAddOrUpdate(R resource);

  void cleanupForResource(R resource);

  Set<ResourceID> getSecondaryResources(ResourceID primary);
}
