package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
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
    final var resourceID = execution.resourceID();
    final var metadata = execution.metadata();
    final var tags = new ArrayList<String>(metadata.size() + 4);
    tags.addAll(List.of(
        "controller", name,
        "resource.name", resourceID.getName(),
        "resource.namespace", resourceID.getNamespace().orElse(""),
        "resource.scope", resourceID.getNamespace().isPresent() ? "namespace" : "cluster"));
    final var gvk = (GroupVersionKind) metadata.get(Constants.RESOURCE_GVK_KEY);
    if (gvk != null) {
      tags.addAll(List.of(
          "resource.group", gvk.group,
          "resource.version", gvk.version,
          "resource.kind", gvk.kind));
    }
    final var timer =
        Timer.builder(execName)
            .tags(tags.toArray(new String[0]))
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(registry);
    try {
      final var result = timer.record(() -> {
        try {
          return execution.execute();
        } catch (Exception e) {
          throw new OperatorException(e);
        }
      });
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

  public void receivedEvent(Event event, Map<String, Object> metadata) {
    incrementCounter(event.getRelatedCustomResourceID(), "events.received",
        metadata,
        "event", event.getClass().getSimpleName());
  }

  @Override
  public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {
    incrementCounter(resourceID, "events.delete", metadata);
  }

  @Override
  public void reconcileCustomResource(ResourceID resourceID, RetryInfo retryInfoNullable,
      Map<String, Object> metadata) {
    Optional<RetryInfo> retryInfo = Optional.ofNullable(retryInfoNullable);
    incrementCounter(resourceID, RECONCILIATIONS + "started",
        metadata,
        RECONCILIATIONS + "retries.number",
        "" + retryInfo.map(RetryInfo::getAttemptCount).orElse(0),
        RECONCILIATIONS + "retries.last",
        "" + retryInfo.map(RetryInfo::isLastAttempt).orElse(true));
  }

  @Override
  public void finishedReconciliation(ResourceID resourceID, Map<String, Object> metadata) {
    incrementCounter(resourceID, RECONCILIATIONS + "success", metadata);
  }

  public void failedReconciliation(ResourceID resourceID, Exception exception,
      Map<String, Object> metadata) {
    var cause = exception.getCause();
    if (cause == null) {
      cause = exception;
    } else if (cause instanceof RuntimeException) {
      cause = cause.getCause() != null ? cause.getCause() : cause;
    }
    incrementCounter(resourceID, RECONCILIATIONS + "failed", metadata, "exception",
        cause.getClass().getSimpleName());
  }

  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return registry.gaugeMapSize(PREFIX + name + ".size", Collections.emptyList(), map);
  }

  private void incrementCounter(ResourceID id, String counterName, Map<String, Object> metadata,
      String... additionalTags) {
    final var additionalTagsNb =
        additionalTags != null && additionalTags.length > 0 ? additionalTags.length : 0;
    final var metadataNb = metadata != null ? metadata.size() : 0;
    final var tags = new ArrayList<String>(6 + additionalTagsNb + metadataNb);
    tags.addAll(List.of(
        "name", id.getName(),
        "namespace", id.getNamespace().orElse(""),
        "scope", id.getNamespace().isPresent() ? "namespace" : "cluster"));
    if (additionalTagsNb > 0) {
      tags.addAll(List.of(additionalTags));
    }
    if (metadataNb > 0) {
      final var gvk = (GroupVersionKind) metadata.get(Constants.RESOURCE_GVK_KEY);
      tags.addAll(List.of(
          "group", gvk.group,
          "version", gvk.version,
          "kind", gvk.kind));
    }
    registry.counter(PREFIX + counterName, tags.toArray(new String[0])).increment();
  }
}
