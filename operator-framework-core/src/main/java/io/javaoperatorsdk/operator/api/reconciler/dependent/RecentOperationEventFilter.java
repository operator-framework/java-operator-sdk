package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface RecentOperationEventFilter<R> extends RecentOperationCacheFiller<R> {

  void prepareForCreateOrUpdateEventFiltering(ResourceID resourceID, R resource);

  void cleanupOnCreateOrUpdateEventFiltering(ResourceID resourceID);

}
