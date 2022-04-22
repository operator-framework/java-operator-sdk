package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface RecentOperationCacheFiller<R> {

  void handleRecentResourceCreate(ResourceID resourceID, R resource);

  void handleRecentResourceUpdate(ResourceID resourceID, R resource, R previousVersionOfResource);
}
