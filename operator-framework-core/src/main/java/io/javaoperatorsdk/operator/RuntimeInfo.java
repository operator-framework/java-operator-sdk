package io.javaoperatorsdk.operator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.InformerEventSourceHealthIndicator;

@SuppressWarnings("rawtypes")
public class RuntimeInfo {

  private final List<RegisteredController> registeredControllers;

  public RuntimeInfo(List<RegisteredController> registeredControllers) {
    this.registeredControllers = registeredControllers;
  }

  public List<RegisteredController> getRegisteredControllers() {
    return registeredControllers;
  }

  public boolean allEventSourcesAreHealthy() {
    return registeredControllers.stream()
        .filter(rc -> !rc.getControllerHealthInfo().unhealthyEventSources().isEmpty())
        .findFirst().isEmpty();
  }

  public Map<String, Map<String, EventSourceHealthIndicator>> unhealthyEventSources() {
    Map<String, Map<String, EventSourceHealthIndicator>> res = new HashMap<>();
    for (var rc : registeredControllers) {
      res.put(rc.getConfiguration().getName(),
          rc.getControllerHealthInfo().unhealthyEventSources());
    }
    return res;
  }

  public Map<String, Map<String, InformerEventSourceHealthIndicator>> unhealthyInformerEventSources() {
    Map<String, Map<String, InformerEventSourceHealthIndicator>> res = new HashMap<>();
    for (var rc : registeredControllers) {
      res.put(rc.getConfiguration().getName(), rc.getControllerHealthInfo()
          .unhealthyInformerEventSourceHealthIndicators());
    }
    return res;
  }
}
