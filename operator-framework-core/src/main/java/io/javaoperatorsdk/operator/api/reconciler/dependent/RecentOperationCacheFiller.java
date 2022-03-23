package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public interface RecentOperationCacheFiller<R> {

  void handleRecentResourceCreate(ObjectKey objectKey, R resource);

  void handleRecentResourceUpdate(ObjectKey objectKey, R resource, R previousResourceVersion);
}
