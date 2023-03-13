package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.CONTROLLER_NAME;

public class MicrometerMetrics implements Metrics {

  private static final String PREFIX = "operator.sdk.";
  private static final String RECONCILIATIONS = "reconciliations.";
  private static final String RECONCILIATIONS_EXECUTIONS = PREFIX + RECONCILIATIONS + "executions.";
  private static final String RECONCILIATIONS_QUEUE_SIZE = PREFIX + RECONCILIATIONS + "queue.size.";
  private final MeterRegistry registry;
  private final Map<String, AtomicInteger> gauges = new ConcurrentHashMap<>();
  private final Map<ResourceID, Set<Meter.Id>> metersPerResource = new ConcurrentHashMap<>();
  private final ScheduledExecutorService metersCleaner;
  private final int cleanUpDelayInSeconds;

  public MicrometerMetrics(MeterRegistry registry) {
    this(registry, 300, Runtime.getRuntime().availableProcessors());
  }

  public MicrometerMetrics(MeterRegistry registry, int cleanUpDelayInSeconds, int cleaningThreadsNumber) {
    this.registry = registry;
    this.cleanUpDelayInSeconds = cleanUpDelayInSeconds;
    this.metersCleaner = Executors.newScheduledThreadPool(cleaningThreadsNumber);
  }

  @Override
  public void controllerRegistered(Controller<?> controller) {
    String executingThreadsName =
        RECONCILIATIONS_EXECUTIONS + controller.getConfiguration().getName();
    AtomicInteger executingThreads =
        registry.gauge(executingThreadsName,
            gvkTags(controller.getConfiguration().getResourceClass()),
            new AtomicInteger(0));
    gauges.put(executingThreadsName, executingThreads);

    String controllerQueueName =
        RECONCILIATIONS_QUEUE_SIZE + controller.getConfiguration().getName();
    AtomicInteger controllerQueueSize =
        registry.gauge(controllerQueueName,
            gvkTags(controller.getConfiguration().getResourceClass()),
            new AtomicInteger(0));
    gauges.put(controllerQueueName, controllerQueueSize);
  }

  @Override
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
        "resource.scope", getScope(resourceID)));
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

  private static String getScope(ResourceID resourceID) {
    return resourceID.getNamespace().isPresent() ? "namespace" : "cluster";
  }

  @Override
  public void receivedEvent(Event event, Map<String, Object> metadata) {
    incrementCounter(event.getRelatedCustomResourceID(), "events.received",
        metadata,
        "event", event.getClass().getSimpleName());
  }

  @Override
  public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {
    incrementCounter(resourceID, "events.delete", metadata);

    // schedule deletion of meters associated with ResourceID
    metersCleaner.schedule(() -> {
      final var toClean = metersPerResource.get(resourceID);
      if (toClean != null) {
        toClean.forEach(registry::remove);
      }
    }, cleanUpDelayInSeconds, TimeUnit.SECONDS);
  }

  @Override
  public void reconcileCustomResource(HasMetadata resource, RetryInfo retryInfoNullable,
      Map<String, Object> metadata) {
    Optional<RetryInfo> retryInfo = Optional.ofNullable(retryInfoNullable);
    incrementCounter(ResourceID.fromResource(resource), RECONCILIATIONS + "started",
        metadata,
        RECONCILIATIONS + "retries.number",
        String.valueOf(retryInfo.map(RetryInfo::getAttemptCount).orElse(0)),
        RECONCILIATIONS + "retries.last",
        String.valueOf(retryInfo.map(RetryInfo::isLastAttempt).orElse(true)));

    var controllerQueueSize =
        gauges.get(RECONCILIATIONS_QUEUE_SIZE + metadata.get(CONTROLLER_NAME));
    controllerQueueSize.incrementAndGet();
  }

  @Override
  public void finishedReconciliation(HasMetadata resource, Map<String, Object> metadata) {
    incrementCounter(ResourceID.fromResource(resource), RECONCILIATIONS + "success", metadata);
  }

  @Override
  public void reconciliationExecutionStarted(HasMetadata resource, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(RECONCILIATIONS_EXECUTIONS + metadata.get(CONTROLLER_NAME));
    reconcilerExecutions.incrementAndGet();
  }

  @Override
  public void reconciliationExecutionFinished(HasMetadata resource, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(RECONCILIATIONS_EXECUTIONS + metadata.get(CONTROLLER_NAME));
    reconcilerExecutions.decrementAndGet();

    var controllerQueueSize =
        gauges.get(RECONCILIATIONS_QUEUE_SIZE + metadata.get(CONTROLLER_NAME));
    controllerQueueSize.decrementAndGet();
  }

  @Override
  public void failedReconciliation(HasMetadata resource, Exception exception,
      Map<String, Object> metadata) {
    var cause = exception.getCause();
    if (cause == null) {
      cause = exception;
    } else if (cause instanceof RuntimeException) {
      cause = cause.getCause() != null ? cause.getCause() : cause;
    }
    incrementCounter(ResourceID.fromResource(resource), RECONCILIATIONS + "failed", metadata,
        "exception",
        cause.getClass().getSimpleName());
  }

  @Override
  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return registry.gaugeMapSize(PREFIX + name + ".size", Collections.emptyList(), map);
  }

  public static List<Tag> gvkTags(Class<? extends HasMetadata> resourceClass) {
    final var gvk = GroupVersionKind.gvkFor(resourceClass);
    return List.of(Tag.of("group", gvk.group), Tag.of("version", gvk.version),
        Tag.of("kind", gvk.kind));
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
        "scope", getScope(id)));
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
    final var counter = registry.counter(PREFIX + counterName, tags.toArray(new String[0]));
    metersPerResource.computeIfAbsent(id, resourceID -> new HashSet<>()).add(counter.getId());
    counter.increment();
  }

  protected Set<Meter.Id> recordedMeterIdsFor(ResourceID resourceID) {
    return metersPerResource.get(resourceID);
  }
}
