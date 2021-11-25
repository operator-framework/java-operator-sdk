package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface EventSource extends LifecycleAware {

  void setEventHandler(EventHandler eventHandler);

  /**
   * Automatically called when a custom resource is deleted from the cluster.
   *
   * @param customResourceUid - id of custom resource
   */
  default void cleanupForResource(ResourceID customResourceUid) {}
}
