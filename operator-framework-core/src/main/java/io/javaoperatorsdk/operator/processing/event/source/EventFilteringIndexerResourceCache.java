package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface EventFilteringIndexerResourceCache<R extends HasMetadata>
    extends IndexerResourceCache<R> {

  void prepareForCreateOrUpdateEventFiltering(ResourceID resourceID, R resource);

  void cleanupOnCreateOrUpdateEventFiltering(ResourceID resourceID);
}
