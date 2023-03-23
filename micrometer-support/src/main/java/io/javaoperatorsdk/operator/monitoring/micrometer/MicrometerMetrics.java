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

public class MicrometerMetrics implements Metrics {

  private static final String PREFIX = "operator.sdk.";
  private static final String RECONCILIATIONS = "reconciliations.";
  private static final String RECONCILIATIONS_EXECUTIONS = PREFIX + RECONCILIATIONS + "executions.";
  private static final String RECONCILIATIONS_QUEUE_SIZE = PREFIX + RECONCILIATIONS + "queue.size.";
  private static final String NAME = "name";
  private static final String NAMESPACE = "namespace";
  private static final String GROUP = "group";
  private static final String VERSION = "version";
  private static final String KIND = "kind";
  private static final String SCOPE = "scope";
  private static final String METADATA_PREFIX = "resource.";
  private final boolean collectPerResourceMetrics;
  private final MeterRegistry registry;
  private final Map<String, AtomicInteger> gauges = new ConcurrentHashMap<>();
  private final Cleaner cleaner;

  /**
   * Creates a default micrometer-based Metrics implementation, collecting metrics on a per resource
   * basis and not dealing with cleaning these after these resources are deleted. Note that this
   * probably will change in a future release. If you want more control over what the implementation
   * actually does, please use the static factory methods instead.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @deprecated Use the factory methods / builders instead
   */
  @Deprecated
  public MicrometerMetrics(MeterRegistry registry) {
    this(registry, Cleaner.NOOP, true);
  }

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
  public static PerResourceCollectingMicrometerMetricsBuilder newPerResourceCollectingMicrometerMetricsBuilder(
      MeterRegistry registry) {
    return new PerResourceCollectingMicrometerMetricsBuilder(registry);
  }

  /**
   * Creates a micrometer-based Metrics implementation that cleans up {@link Meter}s associated with
   * deleted resources as specified by the (possibly {@code null}) provided {@link Cleaner}
   * instance.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @param cleaner the {@link Cleaner} to use
   * @param collectingPerResourceMetrics whether or not to collect per resource metrics
   */
  private MicrometerMetrics(MeterRegistry registry, Cleaner cleaner,
      boolean collectingPerResourceMetrics) {
    this.registry = registry;
    this.cleaner = cleaner;
    this.collectPerResourceMetrics = collectingPerResourceMetrics;
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
    final var tags = new ArrayList<String>(16);
    tags.add("controller");
    tags.add(name);
    addMetadataTags(resourceID, metadata, tags, true);
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

  private void addMetadataTags(ResourceID resourceID, Map<String, Object> metadata,
      List<String> tags, boolean prefixed) {
    if (collectPerResourceMetrics) {
      addTag(NAME, resourceID.getName(), tags, prefixed);
      addTagOmittingOnEmptyValue(NAMESPACE, resourceID.getNamespace().orElse(""), tags, prefixed);
    }
    addTag(SCOPE, getScope(resourceID), tags, prefixed);
    final var gvk = (GroupVersionKind) metadata.get(Constants.RESOURCE_GVK_KEY);
    if (gvk != null) {
      addTagOmittingOnEmptyValue(GROUP, gvk.group, tags, prefixed);
      addTag(VERSION, gvk.version, tags, prefixed);
      addTag(KIND, gvk.kind, tags, prefixed);
    }
  }

  private static void addTag(String name, String value, List<String> tags, boolean prefixed) {
    tags.add(getPrefixedMetadataTag(name, prefixed));
    tags.add(value);
  }
  private static void addTagOmittingOnEmptyValue(String name, String value, List<String> tags, boolean prefixed) {
    if (value != null && !value.isBlank()) {
      addTag(name, value, tags, prefixed);
    }
  }

  private static String getPrefixedMetadataTag(String tagName, boolean prefixed) {
    return prefixed ? METADATA_PREFIX + tagName : tagName;
  }

  private static String getScope(ResourceID resourceID) {
    return resourceID.getNamespace().isPresent() ? "namespace" : "cluster";
  }

  @Override
  public void receivedEvent(Event event, Map<String, Object> metadata) {
    final String[] tags;
    if (event instanceof ResourceEvent) {
      tags = new String[] {"event", event.getClass().getSimpleName(), "action",
          ((ResourceEvent) event).getAction().toString()};
    } else {
      tags = new String[] {"event", event.getClass().getSimpleName()};
    }

    incrementCounter(event.getRelatedCustomResourceID(), "events.received",
        metadata,
        tags);
  }

  @Override
  public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {
    incrementCounter(resourceID, "events.delete", metadata);

    cleaner.removeMetersFor(resourceID);
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
    if (groupExists(gvk)) {
      return List.of(Tag.of("group", gvk.group), Tag.of("version", gvk.version),
          Tag.of("kind", gvk.kind));
    } else {
      return List.of(Tag.of("version", gvk.version), Tag.of("kind", gvk.kind));
    }
  }

  private void incrementCounter(ResourceID id, String counterName, Map<String, Object> metadata,
      String... additionalTags) {
    final var additionalTagsNb =
        additionalTags != null && additionalTags.length > 0 ? additionalTags.length : 0;
    final var metadataNb = metadata != null ? metadata.size() : 0;
    final var tags = new ArrayList<String>(6 + additionalTagsNb + metadataNb);
    addMetadataTags(id, metadata, tags, false);
    if (additionalTagsNb > 0) {
      tags.addAll(List.of(additionalTags));
    }
    final var counter = registry.counter(PREFIX + counterName, tags.toArray(new String[0]));
    cleaner.recordAssociation(id, counter);
    counter.increment();
  }

  private static boolean groupExists(GroupVersionKind gvk) {
    return gvk.group != null && !gvk.group.isBlank();
  }

  protected Set<Meter.Id> recordedMeterIdsFor(ResourceID resourceID) {
    return cleaner.recordedMeterIdsFor(resourceID);
  }

  protected Cleaner getCleaner() {
    return cleaner;
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
     *        removal of {@link Meter}s associated with deleted resources, defaults to 1 if not
     *        specified or if the provided number is lesser or equal to 0
     */
    public PerResourceCollectingMicrometerMetricsBuilder withCleaningThreadNumber(
        int cleaningThreadsNumber) {
      this.cleaningThreadsNumber = cleaningThreadsNumber <= 0 ? 1 : cleaningThreadsNumber;
      return this;
    }

    /**
     * @param cleanUpDelayInSeconds the number of seconds to wait before {@link Meter}s are removed
     *        for deleted resources, defaults to 1 (meaning meters will be removed one second after
     *        the associated resource is deleted) if not specified or if the provided number is
     *        lesser than 0. Threading and the general interaction model of interacting with the API
     *        server means that it's not possible to ensure that meters are immediately deleted in
     *        all cases so a minimal delay of one second is always enforced
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

    /**
     * Configures the instance to collect metrics on a per-resource basis.
     */
    public PerResourceCollectingMicrometerMetricsBuilder collectingMetricsPerResource() {
      collectingPerResourceMetrics = true;
      return new PerResourceCollectingMicrometerMetricsBuilder(registry);
    }

    /**
     * Configures the instance to only collect metrics per resource **type**, in an aggregate
     * fashion, instead of per resource instance.
     */
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

    private DelayedCleaner(MeterRegistry registry, int cleanUpDelayInSeconds,
        int cleaningThreadsNumber) {
      super(registry);
      this.cleanUpDelayInSeconds = cleanUpDelayInSeconds;
      this.metersCleaner = Executors.newScheduledThreadPool(cleaningThreadsNumber);
    }

    @Override
    public void removeMetersFor(ResourceID resourceID) {
      // schedule deletion of meters associated with ResourceID
      metersCleaner.schedule(() -> super.removeMetersFor(resourceID),
          cleanUpDelayInSeconds, TimeUnit.SECONDS);
    }
  }
}
