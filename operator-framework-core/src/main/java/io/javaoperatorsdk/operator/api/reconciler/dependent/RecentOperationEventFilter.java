package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public interface RecentOperationEventFilter<R> extends RecentOperationCacheFiller<R> {

  void prepareForCreateOrUpdateEventFiltering(ObjectKey objectKey, R resource);

  void cleanupOnCreateOrUpdateEventFiltering(ObjectKey objectKey, R resource);

}
