package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.api.Stoppable;

public interface EventSource extends Stoppable {

  void setEventHandler(EventHandler eventHandler);

  /**
   * Automatically called when a custom resource is deleted from the cluster.
   *
   * @param customResourceUid - id of custom resource
   */
  default void cleanupForCustomResource(CustomResourceID customResourceUid) {}
}
