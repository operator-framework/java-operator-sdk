package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface ComplementaryPrimaryToSecondaryIndex<R extends HasMetadata> {

  void explicitAddOrUpdate(R resource);

  void onCreateOrUpdateEvent(R resourceID);

  Set<ResourceID> getComplementarySecondaryResources(ResourceID primary);
}
