package io.javaoperatorsdk.operator;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.InformerWrappingEventSourceHealthIndicator;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;

/**
 * RuntimeInfo in general is available when operator is fully started. You can use "isStarted" to
 * check that.
 */
@SuppressWarnings("rawtypes")
public class RuntimeInfo {

  private static final Logger log = LoggerFactory.getLogger(RuntimeInfo.class);

  private final Set<RegisteredController> registeredControllers;
  private final Operator operator;

  public RuntimeInfo(Operator operator) {
    this.registeredControllers = Collections.unmodifiableSet(operator.getRegisteredControllers());
    this.operator = operator;
  }

  public boolean isStarted() {
    return operator.isStarted();
  }

  @SuppressWarnings("unused")
  public Set<RegisteredController> getRegisteredControllers() {
    checkIfStarted();
    return registeredControllers;
  }

  private void checkIfStarted() {
    if (!isStarted()) {
      log.warn(
          "Operator not started yet while accessing runtime info, this might lead to an unreliable"
              + " behavior");
    }
  }

  public boolean allEventSourcesAreHealthy() {
    checkIfStarted();
    return registeredControllers.stream()
        .filter(rc -> !rc.getControllerHealthInfo().unhealthyEventSources().isEmpty())
        .findFirst()
        .isEmpty();
  }

  /**
   * @return Aggregated Map with controller related event sources.
   */
  public Map<String, Map<String, EventSourceHealthIndicator>> unhealthyEventSources() {
    checkIfStarted();
    Map<String, Map<String, EventSourceHealthIndicator>> res = new HashMap<>();
    for (var rc : registeredControllers) {
      res.put(
          rc.getConfiguration().getName(), rc.getControllerHealthInfo().unhealthyEventSources());
    }
    return res;
  }

  /**
   * @return Aggregated Map with controller related event sources that wraps an informer. Thus,
   *     either a {@link ControllerEventSource} or an {@link
   *     io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}.
   */
  public Map<String, Map<String, InformerWrappingEventSourceHealthIndicator>>
      unhealthyInformerWrappingEventSourceHealthIndicator() {
    checkIfStarted();
    Map<String, Map<String, InformerWrappingEventSourceHealthIndicator>> res = new HashMap<>();
    for (var rc : registeredControllers) {
      res.put(
          rc.getConfiguration().getName(),
          rc.getControllerHealthInfo().unhealthyInformerEventSourceHealthIndicators());
    }
    return res;
  }

  /**
   * Retrieves the {@link RegisteredController} associated with the specified controller name or
   * {@code null} if no such controller is registered.
   *
   * @param controllerName the name of the {@link RegisteredController} to retrieve
   * @return the {@link RegisteredController} associated with the specified controller name or
   *     {@code null} if no such controller is registered
   * @since 5.1.2
   */
  @SuppressWarnings({"unchecked", "unused"})
  public RegisteredController<? extends HasMetadata> getRegisteredController(
      String controllerName) {
    checkIfStarted();
    return registeredControllers.stream()
        .filter(rc -> rc.getConfiguration().getName().equals(controllerName))
        .findFirst()
        .orElse(null);
  }
}
