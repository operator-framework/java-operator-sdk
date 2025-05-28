package io.javaoperatorsdk.operator.health;

import java.util.Map;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;

@SuppressWarnings("rawtypes")
public class ControllerHealthInfo {

  private final EventSourceManager<?> eventSourceManager;

  public ControllerHealthInfo(EventSourceManager eventSourceManager) {
    this.eventSourceManager = eventSourceManager;
  }

  public Map<String, EventSourceHealthIndicator> eventSourceHealthIndicators() {
    return eventSourceManager.allEventSources().stream()
        .collect(Collectors.toMap(EventSource::name, e -> e));
  }

  public Map<String, EventSourceHealthIndicator> unhealthyEventSources() {
    return eventSourceManager.allEventSources().stream()
        .filter(e -> e.getStatus() == Status.UNHEALTHY)
        .collect(Collectors.toMap(EventSource::name, e -> e));
  }

  public Map<String, InformerWrappingEventSourceHealthIndicator>
      informerEventSourceHealthIndicators() {
    return eventSourceManager.allEventSources().stream()
        .filter(e -> e instanceof InformerWrappingEventSourceHealthIndicator)
        .collect(
            Collectors.toMap(
                EventSource::name, e -> (InformerWrappingEventSourceHealthIndicator) e));
  }

  /**
   * @return Map with event sources that wraps an informer. Thus, either a {@link
   *     ControllerEventSource} or an {@link
   *     io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}.
   */
  public Map<String, InformerWrappingEventSourceHealthIndicator>
      unhealthyInformerEventSourceHealthIndicators() {
    return eventSourceManager.allEventSources().stream()
        .filter(e -> e.getStatus() == Status.UNHEALTHY)
        .filter(e -> e instanceof InformerWrappingEventSourceHealthIndicator)
        .collect(
            Collectors.toMap(
                EventSource::name, e -> (InformerWrappingEventSourceHealthIndicator) e));
  }
}
