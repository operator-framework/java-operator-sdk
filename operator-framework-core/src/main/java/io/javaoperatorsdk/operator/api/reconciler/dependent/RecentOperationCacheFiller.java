package io.javaoperatorsdk.operator.api.reconciler.dependent;

public interface RecentOperationCacheFiller<R> {

  void handleRecentResourceCreate(R resource);

  void handleRecentResourceUpdate(R resource, String previousResourceVersion);
}
