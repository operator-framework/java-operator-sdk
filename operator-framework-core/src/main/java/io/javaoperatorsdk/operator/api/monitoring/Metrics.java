package io.javaoperatorsdk.operator.api.monitoring;

import java.util.Map;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

public interface Metrics {
  Metrics NOOP = new Metrics() {};

  default void receivedEvent(Event event) {}

  default void reconcileCustomResource(CustomResourceID customResourceID) {}

  default void failedReconciliation(CustomResourceID customResourceID,
      RuntimeException exception) {}


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
