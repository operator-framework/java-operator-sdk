package io.javaoperatorsdk.operator;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.InformerWrappingEventSourceHealthIndicator;

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
    this.registeredControllers = operator.getRegisteredControllers();
    this.operator = operator;
  }

  public boolean isStarted() {
    return operator.isStarted();
  }

  public Set<RegisteredController> getRegisteredControllers() {
    checkIfStarted();
    return registeredControllers;
  }

  private void checkIfStarted() {
    if (!isStarted()) {
      log.warn(
          "Operator not started yet while accessing runtime info, this might lead to an unreliable behavior");
    }
  }

  public boolean allEventSourcesAreHealthy() {
    checkIfStarted();
    return registeredControllers.stream()
        .filter(rc -> !rc.getControllerHealthInfo().unhealthyEventSources().isEmpty())
        .findFirst().isEmpty();
  }

  /**
   * @return Aggregated Map with controller related event sources.
   */

  public Map<String, Map<String, EventSourceHealthIndicator>> unhealthyEventSources() {
    checkIfStarted();
    Map<String, Map<String, EventSourceHealthIndicator>> res = new HashMap<>();
    for (var rc : registeredControllers) {
      res.put(rc.getConfiguration().getName(),
          rc.getControllerHealthInfo().unhealthyEventSources());
    }
    return res;
  }

  /**
   * @return Aggregated Map with controller related event sources that wraps an informer. Thus,
   *         either a
   *         {@link io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource}
   *         or an
   *         {@link io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}.
   */
  public Map<String, Map<String, InformerWrappingEventSourceHealthIndicator>> unhealthyInformerWrappingEventSourceHealthIndicator() {
    checkIfStarted();
    Map<String, Map<String, InformerWrappingEventSourceHealthIndicator>> res = new HashMap<>();
    for (var rc : registeredControllers) {
      res.put(rc.getConfiguration().getName(), rc.getControllerHealthInfo()
          .unhealthyInformerEventSourceHealthIndicators());
    }
    return res;
  }
}
