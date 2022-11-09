package io.javaoperatorsdk.operator.health;

import java.util.Map;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

@SuppressWarnings("rawtypes")
public class ControllerHealthInfo {

  private EventSourceManager<?> eventSourceManager;

  public ControllerHealthInfo(EventSourceManager eventSourceManager) {
    this.eventSourceManager = eventSourceManager;
  }

  public Map<String, EventSourceHealthIndicator> eventSourceHealthIndicators() {
    return eventSourceManager.allEventSources().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<String, EventSourceHealthIndicator> unhealthyEventSources() {
    return eventSourceManager.allEventSources().entrySet().stream()
        .filter(e -> e.getValue().getStatus() == Status.UNHEALTHY)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<String, InformerWrappingEventSourceHealthIndicator> informerEventSourceHealthIndicators() {
    return eventSourceManager.allEventSources().entrySet().stream()
        .filter(e -> e.getValue() instanceof InformerWrappingEventSourceHealthIndicator)
        .collect(Collectors.toMap(Map.Entry::getKey,
            e -> (InformerWrappingEventSourceHealthIndicator) e.getValue()));

  }

  /**
   * @return Map with event sources that wraps an informer. Thus, either a
   *         {@link io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource}
   *         or an
   *         {@link io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}.
   */
  public Map<String, InformerWrappingEventSourceHealthIndicator> unhealthyInformerEventSourceHealthIndicators() {
    return eventSourceManager.allEventSources().entrySet().stream()
        .filter(e -> e.getValue().getStatus() == Status.UNHEALTHY)
        .filter(e -> e.getValue() instanceof InformerWrappingEventSourceHealthIndicator)
        .collect(Collectors.toMap(Map.Entry::getKey,
            e -> (InformerWrappingEventSourceHealthIndicator) e.getValue()));
  }

}
