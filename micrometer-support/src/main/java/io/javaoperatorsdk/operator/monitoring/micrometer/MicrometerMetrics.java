/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

public class MicrometerMetrics implements Metrics {

  private static final String SUCCESS_SUFFIX = "success";
  private static final String FAILURE_SUFFIX = "failure";
  private static final String PREFIX = "operator.sdk.";
  private static final String RECONCILIATIONS = "reconciliations.";
  private static final String RECONCILIATIONS_FAILED = PREFIX + RECONCILIATIONS + FAILURE_SUFFIX;
  private static final String RECONCILIATIONS_SUCCESS = PREFIX + RECONCILIATIONS + SUCCESS_SUFFIX;
  private static final String RECONCILIATIONS_RETRIES_NUMBER =
      PREFIX + RECONCILIATIONS + "retries.number";
  private static final String RECONCILIATIONS_STARTED = PREFIX + RECONCILIATIONS + "started";
  private static final String RECONCILIATIONS_EXECUTIONS = PREFIX + RECONCILIATIONS + "executions";
  private static final String RECONCILIATIONS_QUEUE_SIZE = PREFIX + RECONCILIATIONS + "active";
  private static final String NAME = "name";
  private static final String NAMESPACE = "namespace";
  private static final String GROUP = "group";
  private static final String VERSION = "version";
  private static final String KIND = "kind";
  private static final String SCOPE = "scope";
  private static final String METADATA_PREFIX = "resource.";
  private static final String CONTROLLERS = "controllers.";
  private static final String RECONCILIATION_EXECUTION_TIME =
      PREFIX + RECONCILIATIONS + "execution" + ".duration";
  private static final String CONTROLLERS_SUCCESSFUL_EXECUTION =
      PREFIX + CONTROLLERS + SUCCESS_SUFFIX;
  private static final String CONTROLLERS_FAILED_EXECUTION = PREFIX + CONTROLLERS + FAILURE_SUFFIX;
  private static final String CONTROLLER = "controller";
  private static final String CONTROLLER_NAME = CONTROLLER + ".name";
  private static final String EVENT = "event";
  private static final String ACTION = "action";
  private static final String EVENTS_RECEIVED = PREFIX + "events.received";
  private static final String EVENTS_DELETE = PREFIX + "events.delete";
  private static final String CLUSTER = "cluster";
  private static final String SIZE_SUFFIX = ".size";
  private static final String UNKNOWN_ACTION = "UNKNOWN";
  private final boolean collectPerResourceMetrics;
  private final MeterRegistry registry;
  // todo double check if we actually need this
  private final Map<String, AtomicInteger> gauges = new ConcurrentHashMap<>();
  private final Consumer<Timer.Builder> timerConfig;

  /**
   * Creates a MicrometerMetrics instance configured to not collect per-resource metrics, just
   * aggregates per resource **type**
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @return a MicrometerMetrics instance configured to not collect per-resource metrics
   */
  public static MicrometerMetrics withoutPerResourceMetrics(MeterRegistry registry) {
    return new MicrometerMetrics(registry, false, null);
  }

  /**
   * Creates a new builder to configure how the eventual MicrometerMetrics instance will behave.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @return a MicrometerMetrics instance configured to not collect per-resource metrics
   * @see MicrometerMetricsBuilder
   */
  public static MicrometerMetricsBuilder newMicrometerMetricsBuilder(MeterRegistry registry) {
    return new MicrometerMetricsBuilder(registry);
  }

  /**
   * Creates a new builder to configure how the eventual MicrometerMetrics instance will behave,
   * pre-configuring it to collect metrics per resource.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @return a MicrometerMetrics instance configured to not collect per-resource metrics
   * @see PerResourceCollectingMicrometerMetricsBuilder
   */
  public static PerResourceCollectingMicrometerMetricsBuilder
      newPerResourceCollectingMicrometerMetricsBuilder(MeterRegistry registry) {
    return new PerResourceCollectingMicrometerMetricsBuilder(registry, null);
  }

  // todo as v2 class
  // todo make backwards compatible
  /**
   * Creates a micrometer-based Metrics implementation.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @param collectingPerResourceMetrics whether to collect per resource metrics
   * @param timerConfig optional configuration for timers, defaults to publishing percentiles 0.5,
   *     0.95, 0.99 and histogram
   */
  private MicrometerMetrics(
      MeterRegistry registry,
      boolean collectingPerResourceMetrics,
      Consumer<Timer.Builder> timerConfig) {
    this.registry = registry;
    this.collectPerResourceMetrics = collectingPerResourceMetrics;
    this.timerConfig =
        timerConfig != null
            ? timerConfig
            : builder -> builder.publishPercentiles(0.5, 0.95, 0.99).publishPercentileHistogram();
  }

  @Override
  public void controllerRegistered(Controller<? extends HasMetadata> controller) {
    final var configuration = controller.getConfiguration();
    final var name = configuration.getName();
    final var executingThreadsRefName = reconciliationExecutionGaugeRefName(name);
    final var resourceClass = configuration.getResourceClass();
    final var tags = new ArrayList<Tag>();
    tags.add(Tag.of(CONTROLLER_NAME, name));
    addGVKTags(GroupVersionKind.gvkFor(resourceClass), tags, false);
    AtomicInteger executingThreads =
        registry.gauge(RECONCILIATIONS_EXECUTIONS, tags, new AtomicInteger(0));
    gauges.put(executingThreadsRefName, executingThreads);

    final var controllerQueueRefName = controllerQueueSizeGaugeRefName(name);
    AtomicInteger controllerQueueSize =
        registry.gauge(RECONCILIATIONS_QUEUE_SIZE, tags, new AtomicInteger(0));
    gauges.put(controllerQueueRefName, controllerQueueSize);
  }

  private static @NonNull String reconciliationExecutionGaugeRefName(String controllerName) {
    return RECONCILIATIONS_EXECUTIONS + "." + controllerName;
  }

  private static @NonNull String controllerQueueSizeGaugeRefName(String controllerName) {
    return RECONCILIATIONS_QUEUE_SIZE + "." + controllerName;
  }

  // todo does it make sense to have both controller and reconciler execution counters?
  @Override
  public <T> T timeControllerExecution(ControllerExecution<T> execution) {
    final var name = execution.controllerName();
    final var resourceID = execution.resourceID();
    final var metadata = execution.metadata();
    final var tags = new ArrayList<Tag>(16);
    tags.add(Tag.of(CONTROLLER, name));
    addMetadataTags(resourceID, metadata, tags, true);
    final var timerBuilder = Timer.builder(RECONCILIATION_EXECUTION_TIME).tags(tags);
    timerConfig.accept(timerBuilder);
    final var timer = timerBuilder.register(registry);
    try {
      final var result =
          timer.record(
              () -> {
                try {
                  return execution.execute();
                } catch (Exception e) {
                  throw new OperatorException(e);
                }
              });
      registry.counter(CONTROLLERS_SUCCESSFUL_EXECUTION, CONTROLLER, name).increment();
      return result;
    } catch (Exception e) {
      registry.counter(CONTROLLERS_FAILED_EXECUTION, CONTROLLER, name).increment();
      throw e;
    }
  }

  @Override
  public void receivedEvent(Event event, Map<String, Object> metadata) {
    if (event instanceof ResourceEvent resourceEvent) {
      incrementCounter(
          event.getRelatedCustomResourceID(),
          EVENTS_RECEIVED,
          metadata,
          Tag.of(EVENT, event.getClass().getSimpleName()),
          Tag.of(ACTION, resourceEvent.getAction().toString()));
    } else {
      incrementCounter(
          event.getRelatedCustomResourceID(),
          EVENTS_RECEIVED,
          metadata,
          Tag.of(EVENT, event.getClass().getSimpleName()),
          Tag.of(ACTION, UNKNOWN_ACTION));
    }
  }

  @Override
  public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {
    incrementCounter(resourceID, EVENTS_DELETE, metadata);
  }

  @Override
  public void reconcileCustomResource(
      HasMetadata resource, RetryInfo retryInfoNullable, Map<String, Object> metadata) {
    Optional<RetryInfo> retryInfo = Optional.ofNullable(retryInfoNullable);
    ResourceID resourceID = ResourceID.fromResource(resource);

    // Record the counter without retry tags
    incrementCounter(resourceID, RECONCILIATIONS_STARTED, metadata);

    // todo add metric with for resources in exhaisted retry
    // Update retry number gauge
    int retryNumber = retryInfo.map(RetryInfo::getAttemptCount).orElse(0);
    updateGauge(resourceID, metadata, RECONCILIATIONS_RETRIES_NUMBER, retryNumber);

    var controllerQueueSize =
        gauges.get(controllerQueueSizeGaugeRefName(metadata.get(CONTROLLER_NAME).toString()));
    controllerQueueSize.incrementAndGet();
  }

  @Override
  public void successfullyFinishedReconciliation(
      HasMetadata resource, Map<String, Object> metadata) {
    ResourceID resourceID = ResourceID.fromResource(resource);
    incrementCounter(resourceID, RECONCILIATIONS_SUCCESS, metadata);

    // Reset retry gauges on successful reconciliation
    updateGauge(resourceID, metadata, RECONCILIATIONS_RETRIES_NUMBER, 0);
  }

  @Override
  public void reconciliationExecutionStarted(HasMetadata resource, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(reconciliationExecutionGaugeRefName(metadata.get(CONTROLLER_NAME).toString()));
    reconcilerExecutions.incrementAndGet();
  }

  @Override
  public void reconciliationExecutionFinished(HasMetadata resource, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(reconciliationExecutionGaugeRefName(metadata.get(CONTROLLER_NAME).toString()));
    reconcilerExecutions.decrementAndGet();

    var controllerQueueSize =
        gauges.get(controllerQueueSizeGaugeRefName(metadata.get(CONTROLLER_NAME).toString()));
    controllerQueueSize.decrementAndGet();
  }

  @Override
  public void failedReconciliation(
      HasMetadata resource, Exception exception, Map<String, Object> metadata) {
    incrementCounter(ResourceID.fromResource(resource), RECONCILIATIONS_FAILED, metadata);
  }

  @Override
  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return registry.gaugeMapSize(PREFIX + name + SIZE_SUFFIX, Collections.emptyList(), map);
  }

  private void addMetadataTags(
      ResourceID resourceID, Map<String, Object> metadata, List<Tag> tags, boolean prefixed) {
    if (collectPerResourceMetrics) {
      addTag(NAME, resourceID.getName(), tags, prefixed);
      addTagOmittingOnEmptyValue(NAMESPACE, resourceID.getNamespace().orElse(null), tags, prefixed);
    }
    addTag(SCOPE, getScope(resourceID), tags, prefixed);
    final var gvk = (GroupVersionKind) metadata.get(Constants.RESOURCE_GVK_KEY);
    if (gvk != null) {
      addGVKTags(gvk, tags, prefixed);
    }
  }

  private static void addTag(String name, String value, List<Tag> tags, boolean prefixed) {
    tags.add(Tag.of(getPrefixedMetadataTag(name, prefixed), value));
  }

  private static void addTagOmittingOnEmptyValue(
      String name, String value, List<Tag> tags, boolean prefixed) {
    if (value != null && !value.isBlank()) {
      addTag(name, value, tags, prefixed);
    }
  }

  private static String getPrefixedMetadataTag(String tagName, boolean prefixed) {
    return prefixed ? METADATA_PREFIX + tagName : tagName;
  }

  private static String getScope(ResourceID resourceID) {
    return resourceID.getNamespace().isPresent() ? NAMESPACE : CLUSTER;
  }

  private static void addGVKTags(GroupVersionKind gvk, List<Tag> tags, boolean prefixed) {
    addTagOmittingOnEmptyValue(GROUP, gvk.getGroup(), tags, prefixed);
    addTag(VERSION, gvk.getVersion(), tags, prefixed);
    addTag(KIND, gvk.getKind(), tags, prefixed);
  }

  private void incrementCounter(
      ResourceID id, String counterName, Map<String, Object> metadata, Tag... additionalTags) {
    final var additionalTagsNb =
        additionalTags != null && additionalTags.length > 0 ? additionalTags.length : 0;
    final var metadataNb = metadata != null ? metadata.size() : 0;
    final var tags = new ArrayList<Tag>(6 + additionalTagsNb + metadataNb);
    addMetadataTags(id, metadata, tags, false);
    if (additionalTagsNb > 0) {
      tags.addAll(List.of(additionalTags));
    }

    final var counter = registry.counter(counterName, tags);
    counter.increment();
  }

  private void updateGauge(
      ResourceID id, Map<String, Object> metadata, String gaugeName, int value) {
    final var tags = new ArrayList<Tag>(6);
    addMetadataTags(id, metadata, tags, false);

    AtomicInteger gauge =
        gauges.computeIfAbsent(
            gaugeName, key -> registry.gauge(gaugeName, tags, new AtomicInteger(0)));
    gauge.set(value);
  }

  public static class PerResourceCollectingMicrometerMetricsBuilder
      extends MicrometerMetricsBuilder {

    private PerResourceCollectingMicrometerMetricsBuilder(
        MeterRegistry registry, Consumer<Timer.Builder> timerConfig) {
      super(registry);
      this.executionTimerConfig = timerConfig;
    }

    /**
     * Configures the Timer used for timing controller executions. By default, timers are configured
     * to publish percentiles 0.5, 0.95, 0.99 and a percentile histogram. You can set: {@code
     * .minimumExpectedValue(Duration.ofMillis(...)).maximumExpectedValue(Duration.ofSeconds(...)) }
     * so micrometer can create the buckets for you.
     *
     * @param executionTimerConfig a consumer that will configure the Timer.Builder. The builder
     *     will already have the metric name and tags set.
     * @return this builder for method chaining
     */
    @Override
    public PerResourceCollectingMicrometerMetricsBuilder withExecutionTimerConfig(
        Consumer<Timer.Builder> executionTimerConfig) {
      this.executionTimerConfig = executionTimerConfig;
      return this;
    }

    @Override
    public MicrometerMetrics build() {
      return new MicrometerMetrics(registry, true, executionTimerConfig);
    }
  }

  public static class MicrometerMetricsBuilder {
    protected final MeterRegistry registry;
    private boolean collectingPerResourceMetrics = true;
    protected Consumer<Timer.Builder> executionTimerConfig = null;

    private MicrometerMetricsBuilder(MeterRegistry registry) {
      this.registry = registry;
    }

    /**
     * Configures the Timer used for timing controller executions. By default, timers are configured
     * to publish percentiles 0.5, 0.95, 0.99 and a percentile histogram.
     *
     * @param executionTimerConfig a consumer that will configure the Timer.Builder. The builder
     *     will already have the metric name and tags set.
     * @return this builder for method chaining
     */
    public MicrometerMetricsBuilder withExecutionTimerConfig(
        Consumer<Timer.Builder> executionTimerConfig) {
      this.executionTimerConfig = executionTimerConfig;
      return this;
    }

    /** Configures the instance to collect metrics on a per-resource basis. */
    @SuppressWarnings("unused")
    public PerResourceCollectingMicrometerMetricsBuilder collectingMetricsPerResource() {
      collectingPerResourceMetrics = true;
      return new PerResourceCollectingMicrometerMetricsBuilder(registry, executionTimerConfig);
    }

    /**
     * Configures the instance to only collect metrics per resource **type**, in an aggregate
     * fashion, instead of per resource instance.
     */
    @SuppressWarnings("unused")
    public MicrometerMetricsBuilder notCollectingMetricsPerResource() {
      collectingPerResourceMetrics = false;
      return this;
    }

    public MicrometerMetrics build() {
      return new MicrometerMetrics(registry, collectingPerResourceMetrics, executionTimerConfig);
    }
  }
}
