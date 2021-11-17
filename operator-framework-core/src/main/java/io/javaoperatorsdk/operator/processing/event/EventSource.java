package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.api.LifecycleAware;

public interface EventSource extends LifecycleAware {

  void setEventHandler(EventHandler eventHandler);

  /**
   * Automatically called when a custom resource is deleted from the cluster.
   *
   * @param customResourceUid - id of custom resource
   */
  default void cleanupForResource(CustomResourceID customResourceUid) {}
}
