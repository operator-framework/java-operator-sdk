package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface PrimaryToSecondaryIndex<R extends HasMetadata> {

  void onAddOrUpdate(R resource);

  void onDelete(R resource);

  Set<ResourceID> getSecondaryResources(ResourceID primary);
}
