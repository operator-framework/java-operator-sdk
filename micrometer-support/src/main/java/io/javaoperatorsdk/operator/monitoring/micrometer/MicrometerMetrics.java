package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MicrometerMetrics implements Metrics {

  private static final String PREFIX = "operator.sdk.";
  private static final String RECONCILIATIONS = "reconciliations.";
  private final MeterRegistry registry;

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

  public void receivedEvent(Event event) {
    incrementCounter(event.getRelatedCustomResourceID(), "events.received", "event",
        event.getClass().getSimpleName());
  }

  @Override
  public void cleanupDoneFor(CustomResourceID customResourceUid) {
    incrementCounter(customResourceUid, "events.delete");
  }

  public void reconcileCustomResource(CustomResourceID customResourceID,
      RetryInfo retryInfo) {
    incrementCounter(customResourceID, RECONCILIATIONS + "started",
        RECONCILIATIONS + "retries.number", "" + retryInfo.getAttemptCount(),
        RECONCILIATIONS + "retries.last", "" + retryInfo.isLastAttempt());
  }

  @Override
  public void finishedReconciliation(CustomResourceID customResourceID) {
    incrementCounter(customResourceID, RECONCILIATIONS + "success");
  }

  public void failedReconciliation(CustomResourceID customResourceID, RuntimeException exception) {
    var cause = exception.getCause();
    if (cause == null) {
      cause = exception;
    } else if (cause instanceof RuntimeException) {
      cause = cause.getCause() != null ? cause.getCause() : cause;
    }
    incrementCounter(customResourceID, RECONCILIATIONS + "failed", "exception",
        cause.getClass().getSimpleName());
  }

  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return registry.gaugeMapSize(PREFIX + name + ".size", Collections.emptyList(), map);
  }

  private void incrementCounter(CustomResourceID id, String counterName, String... additionalTags) {
    var tags = List.of(
        "name", id.getName(),
        "name", id.getName(), "namespace", id.getNamespace().orElse(""),
        "scope", id.getNamespace().isPresent() ? "namespace" : "cluster");
    if (additionalTags != null && additionalTags.length > 0) {
      tags = new LinkedList<>(tags);
      tags.addAll(List.of(additionalTags));
    }
    registry.counter(PREFIX + counterName, tags.toArray(new String[0])).increment();
  }
}
