package io.javaoperatorsdk.operator.api.monitoring;

import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface Metrics {
  Metrics NOOP = new Metrics() {};

  default void receivedEvent(Event event) {}

  default void reconcileCustomResource(ResourceID resourceID, RetryInfo retryInfo) {}

  default void failedReconciliation(ResourceID resourceID, RuntimeException exception) {}

  default void cleanupDoneFor(ResourceID customResourceUid) {}

  default void finishedReconciliation(ResourceID resourceID) {}


  interface ControllerExecution<T> {
    String name();

    String controllerName();

    String successTypeName(T result);

    T execute();
  }

  default <T> T timeControllerExecution(ControllerExecution<T> execution) {
    return execution.execute();
  }

  default <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return map;
  }
}
