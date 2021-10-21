package io.javaoperatorsdk.operator.micrometer;

import java.util.Collections;
import java.util.Map;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler.EventMonitor;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MicrometerMetrics implements Metrics {

  public static final String PREFIX = "operator.sdk.";
  private final MeterRegistry registry;
  private final EventMonitor monitor = new EventMonitor() {
    @Override
    public void processedEvent(CustomResourceID uid, Event event) {
      incrementProcessedEventsNumber();
    }

    @Override
    public void failedEvent(CustomResourceID uid, Event event) {
      incrementControllerRetriesNumber();
    }
  };

  public MicrometerMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public <T> T timeControllerExecution(ControllerExecution<T> execution) {
    final var name = execution.controllerName();
    final var execName = PREFIX + "controllers.execution." + execution.name();
    final var timer =
        Timer.builder(execName)
            .tags("controller", name)
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(registry);
    try {
      final var result = timer.record(execution::execute);
      final var successType = execution.successTypeName(result);
      registry
          .counter(execName + ".success", "controller", name, "type", successType)
          .increment();
      return result;
    } catch (Exception e) {
      final var exception = e.getClass().getSimpleName();
      registry
          .counter(execName + ".failure", "controller", name, "exception", exception)
          .increment();
      throw e;
    }
  }

  public void incrementControllerRetriesNumber() {
    registry
        .counter(
            PREFIX + "retry.on.exception", "retry", "retryCounter", "type",
            "retryException")
        .increment();

  }

  public void incrementProcessedEventsNumber() {
    registry
        .counter(
            PREFIX + "total.events.received", "events", "totalEvents", "type",
            "eventsReceived")
        .increment();

  }

  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return registry.gaugeMapSize(PREFIX + name + ".size", Collections.emptyList(), map);
  }

  @Override
  public EventMonitor getEventMonitor() {
    return monitor;
  }
}
