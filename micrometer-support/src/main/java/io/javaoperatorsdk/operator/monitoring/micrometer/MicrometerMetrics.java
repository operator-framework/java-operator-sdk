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

import static io.javaoperatorsdk.operator.api.reconciler.Constants.CONTROLLER_NAME;

@Deprecated
public class MicrometerMetrics implements Metrics {

  private static final String PREFIX = "operator.sdk.";
  private static final String RECONCILIATIONS = "reconciliations.";
  private static final String RECONCILIATIONS_FAILED = RECONCILIATIONS + "failed";
  private static final String RECONCILIATIONS_SUCCESS = RECONCILIATIONS + "success";
  private static final String RECONCILIATIONS_RETRIES_LAST = RECONCILIATIONS + "retries.last";
  private static final String RECONCILIATIONS_RETRIES_NUMBER = RECONCILIATIONS + "retries.number";
  private static final String RECONCILIATIONS_STARTED = RECONCILIATIONS + "started";
  private static final String RECONCILIATIONS_EXECUTIONS = PREFIX + RECONCILIATIONS + "executions.";
  private static final String RECONCILIATIONS_QUEUE_SIZE = PREFIX + RECONCILIATIONS + "queue.size.";
  private static final String NAME = "name";
  private static final String NAMESPACE = "namespace";
  private static final String GROUP = "group";
  private static final String VERSION = "version";
  private static final String KIND = "kind";
  private static final String SCOPE = "scope";
  private static final String METADATA_PREFIX = "resource.";
  private static final String CONTROLLERS_EXECUTION = "controllers.execution.";
  private static final String CONTROLLER = "controller";
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

  /**
   * Creates a MicrometerMetrics instance configured to not collect per-resource metrics, just
   * aggregates per resource **type**
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @return a MicrometerMetrics instance configured to not collect per-resource metrics
   */
  public static MicrometerMetrics withoutPerResourceMetrics(MeterRegistry registry) {
    return new MicrometerMetrics(registry, Cleaner.NOOP, false);
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
    return new PerResourceCollectingMicrometerMetricsBuilder(registry);
  }

  /**
   * Creates a micrometer-based Metrics implementation that cleans up {@link Meter}s associated with
   * deleted resources as specified by the (possibly {@code null}) provided {@link Cleaner}
   * instance.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @param cleaner the {@link Cleaner} to use
   * @param collectingPerResourceMetrics whether to collect per resource metrics
   */
  private MicrometerMetrics(
      MeterRegistry registry, Cleaner cleaner, boolean collectingPerResourceMetrics) {
    this.registry = registry;
    this.cleaner = cleaner;
    this.collectPerResourceMetrics = collectingPerResourceMetrics;
  }

  @Override
  public void controllerRegistered(Controller<? extends HasMetadata> controller) {
    final var configuration = controller.getConfiguration();
    final var name = configuration.getName();
    final var executingThreadsName = RECONCILIATIONS_EXECUTIONS + name;
    final var resourceClass = configuration.getResourceClass();
    final var tags = new ArrayList<Tag>(3);
    addGVKTags(GroupVersionKind.gvkFor(resourceClass), tags, false);
    AtomicInteger executingThreads =
        registry.gauge(executingThreadsName, tags, new AtomicInteger(0));
    gauges.put(executingThreadsName, executingThreads);

    final var controllerQueueName = RECONCILIATIONS_QUEUE_SIZE + name;
    AtomicInteger controllerQueueSize =
        registry.gauge(controllerQueueName, tags, new AtomicInteger(0));
    gauges.put(controllerQueueName, controllerQueueSize);
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
    final var timer =
        Timer.builder(execName)
            .tags(tags)
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(registry);
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
  @Deprecated(forRemoval = true)
  public void reconcileCustomResource(
      HasMetadata resource, RetryInfo retryInfoNullable, Map<String, Object> metadata) {
    Optional<RetryInfo> retryInfo = Optional.ofNullable(retryInfoNullable);
    incrementCounter(
        ResourceID.fromResource(resource),
        RECONCILIATIONS_STARTED,
        metadata,
        Tag.of(
            RECONCILIATIONS_RETRIES_NUMBER,
            String.valueOf(retryInfo.map(RetryInfo::getAttemptCount).orElse(0))),
        Tag.of(
            RECONCILIATIONS_RETRIES_LAST,
            String.valueOf(retryInfo.map(RetryInfo::isLastAttempt).orElse(true))));

    var controllerQueueSize =
        gauges.get(RECONCILIATIONS_QUEUE_SIZE + metadata.get(CONTROLLER_NAME));
    controllerQueueSize.incrementAndGet();
  }

  @Override
  public void successfullyFinishedReconciliation(
      HasMetadata resource, Map<String, Object> metadata) {
    incrementCounter(ResourceID.fromResource(resource), RECONCILIATIONS_SUCCESS, metadata);
  }

  @Override
  public void reconciliationExecutionStarted(HasMetadata resource, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(RECONCILIATIONS_EXECUTIONS + metadata.get(CONTROLLER_NAME));
    reconcilerExecutions.incrementAndGet();
  }

  @Override
  public void reconciliationExecutionFinished(
      HasMetadata resource, RetryInfo retryInfo, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(RECONCILIATIONS_EXECUTIONS + metadata.get(CONTROLLER_NAME));
    reconcilerExecutions.decrementAndGet();

    var controllerQueueSize =
        gauges.get(RECONCILIATIONS_QUEUE_SIZE + metadata.get(CONTROLLER_NAME));
    controllerQueueSize.decrementAndGet();
  }

  @Override
  public void failedReconciliation(
      HasMetadata resource, RetryInfo retry, Exception exception, Map<String, Object> metadata) {
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

  protected Set<Meter.Id> recordedMeterIdsFor(ResourceID resourceID) {
    return cleaner.recordedMeterIdsFor(resourceID);
  }

  public static class PerResourceCollectingMicrometerMetricsBuilder
      extends MicrometerMetricsBuilder {

    private int cleaningThreadsNumber;
    private int cleanUpDelayInSeconds;

    private PerResourceCollectingMicrometerMetricsBuilder(MeterRegistry registry) {
      super(registry);
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
      return new MicrometerMetrics(registry, cleaner, true);
    }
  }

  public static class MicrometerMetricsBuilder {
    protected final MeterRegistry registry;
    private boolean collectingPerResourceMetrics = true;

    private MicrometerMetricsBuilder(MeterRegistry registry) {
      this.registry = registry;
    }

    /** Configures the instance to collect metrics on a per-resource basis. */
    @SuppressWarnings("unused")
    public PerResourceCollectingMicrometerMetricsBuilder collectingMetricsPerResource() {
      collectingPerResourceMetrics = true;
      return new PerResourceCollectingMicrometerMetricsBuilder(registry);
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
      return new MicrometerMetrics(registry, Cleaner.NOOP, collectingPerResourceMetrics);
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
