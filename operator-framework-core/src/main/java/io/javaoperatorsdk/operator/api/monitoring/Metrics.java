package io.javaoperatorsdk.operator.api.monitoring;

import java.util.Collections;
import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface Metrics {
  Metrics NOOP = new Metrics() {};

  String RESOURCE_GROUP_KEY = "josdk.resource.group";
  String RESOURCE_VERSION_KEY = "josdk.resource.version";
  String RESOURCE_KIND_KEY = "josdk.resource.kind";

  default void receivedEvent(Event event) {}

  /**
   *
   * @deprecated Use (and implement) {@link #reconcileCustomResource(ResourceID, RetryInfo, Map)}
   *             instead
   */
  @Deprecated
  default void reconcileCustomResource(ResourceID resourceID, RetryInfo retryInfo) {
    reconcileCustomResource(resourceID, retryInfo, Collections.emptyMap());
  }

  default void reconcileCustomResource(ResourceID resourceID, RetryInfo retryInfo,
      Map<String, Object> metadata) {}

  /**
   *
   * @deprecated Use (and implement) {@link #failedReconciliation(ResourceID, Exception, Map)}
   *             instead
   */
  @Deprecated
  default void failedReconciliation(ResourceID resourceID, Exception exception) {
    failedReconciliation(resourceID, exception, Collections.emptyMap());
  }

  default void failedReconciliation(ResourceID resourceID, Exception exception,
      Map<String, Object> metadata) {}

  /**
   *
   * @deprecated Use (and implement) {@link #cleanupDoneFor(ResourceID, Map)} instead
   */
  @Deprecated
  default void cleanupDoneFor(ResourceID resourceID) {
    cleanupDoneFor(resourceID, Collections.emptyMap());
  }

  default void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {}

  /**
   *
   * @deprecated Use (and implement) {@link #finishedReconciliation(ResourceID, Map)} instead
   */
  @Deprecated
  default void finishedReconciliation(ResourceID resourceID) {
    finishedReconciliation(resourceID, Collections.emptyMap());
  }

  default void finishedReconciliation(ResourceID resourceID, Map<String, Object> metadata) {}


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
