package io.javaoperatorsdk.operator;

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
}
