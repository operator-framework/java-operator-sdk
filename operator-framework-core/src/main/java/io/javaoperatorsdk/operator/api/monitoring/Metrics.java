package io.javaoperatorsdk.operator.api.monitoring;

import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public interface Metrics {
  Metrics NOOP = new Metrics() {};

  default void receivedEvent(Event event) {}

  default void reconcileCustomResource(ObjectKey objectKey, RetryInfo retryInfo) {}

  default void failedReconciliation(ObjectKey objectKey, Exception exception) {}

  default void cleanupDoneFor(ObjectKey customResourceUid) {}

  default void finishedReconciliation(ObjectKey objectKey) {}


  interface ControllerExecution<T> {
    String name();

    String controllerName();

    String successTypeName(T result);

    T execute() throws Exception;
  }

  default <T> T timeControllerExecution(ControllerExecution<T> execution) throws Exception {
    return execution.execute();
  }

  default <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return map;
  }
}
