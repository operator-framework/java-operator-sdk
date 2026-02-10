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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

public class MicrometerMetrics implements Metrics {

  private static final String PREFIX = "operator.sdk.";
  private static final String RECONCILIATIONS = "reconciliations.";
  private static final String RECONCILIATIONS_FAILED = RECONCILIATIONS + "failed";
  private static final String RECONCILIATIONS_SUCCESS = RECONCILIATIONS + "success";
  private static final String RECONCILIATIONS_RETRIES_LAST = RECONCILIATIONS + "retries.last";
  private static final String RECONCILIATIONS_RETRIES_NUMBER = RECONCILIATIONS + "retries.number";
  private static final String RECONCILIATIONS_STARTED = RECONCILIATIONS + "started";
  private static final String RECONCILIATIONS_EXECUTIONS = PREFIX + RECONCILIATIONS + "executions";
  private static final String RECONCILIATIONS_QUEUE_SIZE = PREFIX + RECONCILIATIONS + "queue.size";
  private static final String NAME = "name";
  private static final String NAMESPACE = "namespace";
  private static final String GROUP = "group";
  private static final String VERSION = "version";
  private static final String KIND = "kind";
  private static final String SCOPE = "scope";
  private static final String METADATA_PREFIX = "resource.";
  private static final String CONTROLLERS_EXECUTION = "controllers.execution.";
  private static final String CONTROLLER = "controller";
  private static final String CONTROLLER_NAME = CONTROLLER + ".name";
  private static final String SUCCESS_SUFFIX = ".success";
  private static final String FAILURE_SUFFIX = ".failure";
  private static final String TYPE = "type";
  private static final String EXCEPTION = "exception";
  private static final String EVENT = "event";
  private static final String ACTION = "action";
  private static final String EVENTS_RECEIVED = "events.received";
  private static final String EVENTS_DELETE = "events.delete";
  private static final String CLUSTER = "cluster";
  private static final String SIZE_SUFFIX = ".size";
  private static final String UNKNOWN_ACTION = "UNKNOWN";
  private final boolean collectPerResourceMetrics;
  private final MeterRegistry registry;
  private final Map<String, AtomicInteger> gauges = new ConcurrentHashMap<>();
  private final Cleaner cleaner;
  private final Consumer<Timer.Builder> timerConfig;

  /**
   * Creates a MicrometerMetrics instance configured to not collect per-resource metrics, just
   * aggregates per resource **type**
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @return a MicrometerMetrics instance configured to not collect per-resource metrics
   */
  public static MicrometerMetrics withoutPerResourceMetrics(MeterRegistry registry) {
    return new MicrometerMetrics(registry, Cleaner.NOOP, false, null);
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

  /**
   * Creates a micrometer-based Metrics implementation that cleans up {@link Meter}s associated with
   * deleted resources as specified by the (possibly {@code null}) provided {@link Cleaner}
   * instance.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @param cleaner the {@link Cleaner} to use
   * @param collectingPerResourceMetrics whether to collect per resource metrics
   * @param timerConfig optional configuration for timers, defaults to publishing percentiles 0.5,
   *     0.95, 0.99 and histogram
   */
  private MicrometerMetrics(
      MeterRegistry registry,
      Cleaner cleaner,
      boolean collectingPerResourceMetrics,
      Consumer<Timer.Builder> timerConfig) {
    this.registry = registry;
    this.cleaner = cleaner;
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

  @Override
  public <T> T timeControllerExecution(ControllerExecution<T> execution) {
    final var name = execution.controllerName();
    final var execName = PREFIX + CONTROLLERS_EXECUTION + execution.name();
    final var resourceID = execution.resourceID();
    final var metadata = execution.metadata();
    final var tags = new ArrayList<Tag>(16);
    tags.add(Tag.of(CONTROLLER, name));
    addMetadataTags(resourceID, metadata, tags, true);
    final var timerBuilder = Timer.builder(execName).tags(tags);
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
      final var successType = execution.successTypeName(result);
      registry.counter(execName + SUCCESS_SUFFIX, CONTROLLER, name, TYPE, successType).increment();
      return result;
    } catch (Exception e) {
      final var exception = e.getClass().getSimpleName();
      registry
          .counter(execName + FAILURE_SUFFIX, CONTROLLER, name, EXCEPTION, exception)
          .increment();
      throw e;
    }
  }

  @Override
  public void receivedEvent(Event event, Map<String, Object> metadata) {
    if (event instanceof ResourceEvent) {
      incrementCounter(
          event.getRelatedCustomResourceID(),
          EVENTS_RECEIVED,
          metadata,
          Tag.of(EVENT, event.getClass().getSimpleName()),
          Tag.of(ACTION, ((ResourceEvent) event).getAction().toString()));
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

    cleaner.removeMetersFor(resourceID);
  }

  @Override
  public void reconcileCustomResource(
      HasMetadata resource, RetryInfo retryInfoNullable, Map<String, Object> metadata) {
    Optional<RetryInfo> retryInfo = Optional.ofNullable(retryInfoNullable);
    ResourceID resourceID = ResourceID.fromResource(resource);

    // Record the counter without retry tags
    incrementCounter(resourceID, RECONCILIATIONS_STARTED, metadata);

    // Update retry number gauge
    int retryNumber = retryInfo.map(RetryInfo::getAttemptCount).orElse(0);
    updateGauge(resourceID, metadata, RECONCILIATIONS_RETRIES_NUMBER, retryNumber);

    // Update retry last attempt gauge (1 for true, 0 for false)
    int isLastAttempt = retryInfo.map(RetryInfo::isLastAttempt).orElse(true) ? 1 : 0;
    updateGauge(resourceID, metadata, RECONCILIATIONS_RETRIES_LAST, isLastAttempt);

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
    updateGauge(resourceID, metadata, RECONCILIATIONS_RETRIES_LAST, 0);
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
    var cause = exception.getCause();
    if (cause == null) {
      cause = exception;
    } else if (cause instanceof RuntimeException) {
      cause = cause.getCause() != null ? cause.getCause() : cause;
    }
    incrementCounter(
        ResourceID.fromResource(resource),
        RECONCILIATIONS_FAILED,
        metadata,
        Tag.of(EXCEPTION, cause.getClass().getSimpleName()));
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

    final var counter = registry.counter(PREFIX + counterName, tags);
    cleaner.recordAssociation(id, counter);
    counter.increment();
  }

  private void updateGauge(
      ResourceID id, Map<String, Object> metadata, String gaugeName, int value) {
    final var tags = new ArrayList<Tag>(6);
    addMetadataTags(id, metadata, tags, false);

    final var gaugeRefName = buildGaugeRefName(id, gaugeName);
    AtomicInteger gauge =
        gauges.computeIfAbsent(
            gaugeRefName,
            key -> {
              AtomicInteger newGauge =
                  registry.gauge(PREFIX + gaugeName, tags, new AtomicInteger(0));
              // Find the meter in the registry and record it for cleanup
              var meter = registry.find(PREFIX + gaugeName).tags(tags).gauge();
              if (meter != null) {
                cleaner.recordAssociation(id, meter);
              }
              return newGauge;
            });
    gauge.set(value);
  }

  private String buildGaugeRefName(ResourceID id, String gaugeName) {
    return gaugeName + "." + id.getName() + "." + id.getNamespace().orElse(CLUSTER);
  }

  protected Set<Meter.Id> recordedMeterIdsFor(ResourceID resourceID) {
    return cleaner.recordedMeterIdsFor(resourceID);
  }

  public static class PerResourceCollectingMicrometerMetricsBuilder
      extends MicrometerMetricsBuilder {

    private int cleaningThreadsNumber;
    private int cleanUpDelayInSeconds;

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

    /**
     * @param cleaningThreadsNumber the maximal number of threads that can be assigned to the
     *     removal of {@link Meter}s associated with deleted resources, defaults to 1 if not
     *     specified or if the provided number is lesser or equal to 0
     */
    public PerResourceCollectingMicrometerMetricsBuilder withCleaningThreadNumber(
        int cleaningThreadsNumber) {
      this.cleaningThreadsNumber = cleaningThreadsNumber <= 0 ? 1 : cleaningThreadsNumber;
      return this;
    }

    /**
     * @param cleanUpDelayInSeconds the number of seconds to wait before {@link Meter}s are removed
     *     for deleted resources, defaults to 1 (meaning meters will be removed one second after the
     *     associated resource is deleted) if not specified or if the provided number is lesser than
     *     0. Threading and the general interaction model of interacting with the API server means
     *     that it's not possible to ensure that meters are immediately deleted in all cases so a
     *     minimal delay of one second is always enforced
     */
    public PerResourceCollectingMicrometerMetricsBuilder withCleanUpDelayInSeconds(
        int cleanUpDelayInSeconds) {
      this.cleanUpDelayInSeconds = Math.max(cleanUpDelayInSeconds, 1);
      return this;
    }

    @Override
    public MicrometerMetrics build() {
      final var cleaner =
          new DelayedCleaner(registry, cleanUpDelayInSeconds, cleaningThreadsNumber);
      return new MicrometerMetrics(registry, cleaner, true, executionTimerConfig);
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
      return new MicrometerMetrics(
          registry, Cleaner.NOOP, collectingPerResourceMetrics, executionTimerConfig);
    }
  }

  interface Cleaner {
    Cleaner NOOP = new Cleaner() {};

    default void removeMetersFor(ResourceID resourceID) {}

    default void recordAssociation(ResourceID resourceID, Meter meter) {}

    default Set<Meter.Id> recordedMeterIdsFor(ResourceID resourceID) {
      return Collections.emptySet();
    }
  }

  static class DefaultCleaner implements Cleaner {
    private final Map<ResourceID, Set<Meter.Id>> metersPerResource = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    private DefaultCleaner(MeterRegistry registry) {
      this.registry = registry;
    }

    @Override
    public void removeMetersFor(ResourceID resourceID) {
      // remove each meter
      final var toClean = metersPerResource.get(resourceID);
      if (toClean != null) {
        toClean.forEach(registry::remove);
      }
      // then clean-up local recording of associations
      metersPerResource.remove(resourceID);
    }

    @Override
    public void recordAssociation(ResourceID resourceID, Meter meter) {
      metersPerResource.computeIfAbsent(resourceID, id -> new HashSet<>()).add(meter.getId());
    }

    @Override
    public Set<Meter.Id> recordedMeterIdsFor(ResourceID resourceID) {
      return metersPerResource.get(resourceID);
    }
  }

  static class DelayedCleaner extends MicrometerMetrics.DefaultCleaner {
    private final ScheduledExecutorService metersCleaner;
    private final int cleanUpDelayInSeconds;

    private DelayedCleaner(
        MeterRegistry registry, int cleanUpDelayInSeconds, int cleaningThreadsNumber) {
      super(registry);
      this.cleanUpDelayInSeconds = cleanUpDelayInSeconds;
      this.metersCleaner = Executors.newScheduledThreadPool(cleaningThreadsNumber);
    }

    @Override
    public void removeMetersFor(ResourceID resourceID) {
      // schedule deletion of meters associated with ResourceID
      metersCleaner.schedule(
          () -> super.removeMetersFor(resourceID), cleanUpDelayInSeconds, TimeUnit.SECONDS);
    }
  }
}
