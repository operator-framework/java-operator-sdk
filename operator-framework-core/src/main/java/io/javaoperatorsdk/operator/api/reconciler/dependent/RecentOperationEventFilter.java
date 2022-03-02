package io.javaoperatorsdk.operator.api.reconciler.dependent;

public interface RecentOperationEventFilter<R> extends RecentOperationCacheFiller<R> {

  void prepareForCreateOrUpdateEventFiltering(R resource);

  void cleanupOnCreateOrUpdateEventFiltering(R resource);

}
