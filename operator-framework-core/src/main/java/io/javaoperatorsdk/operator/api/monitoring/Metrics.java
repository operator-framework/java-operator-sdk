package io.javaoperatorsdk.operator.api.monitoring;

import java.util.Map;

public interface Metrics {
  Metrics NOOP = new Metrics() {};


  interface ControllerExecution<T> {
    String name();

    String controllerName();

    String successTypeName(T result);

    T execute();
  }

  default <T> T timeControllerExecution(ControllerExecution<T> execution) {
    return execution.execute();
  }

  default void incrementControllerRetriesNumber() {}

  default void incrementProcessedEventsNumber() {}

  default <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return map;
  }

  default EventMonitor getEventMonitor() {
    return EventMonitor.NOOP;
  }
}
