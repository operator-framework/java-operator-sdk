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
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

public class MicrometerMetricsV2 implements Metrics {

  private static final String CONTROLLER_NAME = "controller.name";
  private static final String EVENT = "event";
  private static final String ACTION = "action";
  private static final String EVENTS_RECEIVED = "events.received";
  private static final String EVENTS_DELETE = "events.delete";
  private static final String UNKNOWN_ACTION = "UNKNOWN";
  public static final String TOTAL_SUFFIX = ".total";
  private static final String SUCCESS_SUFFIX = "success";
  private static final String FAILURE_SUFFIX = "failure";

  private static final String RECONCILIATIONS = "reconciliations.";

  private static final String RECONCILIATIONS_FAILED =
      RECONCILIATIONS + FAILURE_SUFFIX + TOTAL_SUFFIX;
  private static final String RECONCILIATIONS_SUCCESS =
      RECONCILIATIONS + SUCCESS_SUFFIX + TOTAL_SUFFIX;
  private static final String RECONCILIATIONS_RETRIES_NUMBER =
      RECONCILIATIONS + "retries" + TOTAL_SUFFIX;
  private static final String RECONCILIATIONS_STARTED = RECONCILIATIONS + "started" + TOTAL_SUFFIX;

  private static final String CONTROLLERS = "controllers.";

  private static final String CONTROLLERS_SUCCESSFUL_EXECUTION =
      CONTROLLERS + SUCCESS_SUFFIX + TOTAL_SUFFIX;
  private static final String CONTROLLERS_FAILED_EXECUTION =
      CONTROLLERS + FAILURE_SUFFIX + TOTAL_SUFFIX;

  private static final String RECONCILIATIONS_EXECUTIONS_GAUGE = RECONCILIATIONS + "executions";
  private static final String RECONCILIATIONS_QUEUE_SIZE_GAUGE = RECONCILIATIONS + "active";
  private static final String NUMBER_OF_RESOURCE_GAUGE = "custom_resources";

  private static final String RECONCILIATION_EXECUTION_DURATION =
      RECONCILIATIONS + "execution.seconds";

  private final MeterRegistry registry;
  private final Map<String, AtomicInteger> gauges = new ConcurrentHashMap<>();
  private final Map<String, Timer> executionTimers = new ConcurrentHashMap<>();
  private final Consumer<Timer.Builder> timerConfig;

  /**
   * Creates a new builder to configure how the eventual MicrometerMetricsV2 instance will behave,
   * pre-configuring it to collect metrics per resource.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @return a MicrometerMetricsV2 instance configured to not collect per-resource metrics
   * @see MicrometerMetricsV2Builder
   */
  public static MicrometerMetricsV2Builder newPerResourceCollectingMicrometerMetricsBuilder(
      MeterRegistry registry) {
    return new MicrometerMetricsV2Builder(registry);
  }

  /**
   * Creates a micrometer-based Metrics implementation.
   *
   * @param registry the {@link MeterRegistry} instance to use for metrics recording
   * @param timerConfig optional configuration for timers, defaults to publishing percentiles 0.5,
   *     0.95, 0.99 and histogram
   */
  private MicrometerMetricsV2(MeterRegistry registry, Consumer<Timer.Builder> timerConfig) {
    this.registry = registry;
    this.timerConfig =
        timerConfig != null
            ? timerConfig
            : builder -> builder.publishPercentiles(0.5, 0.95, 0.99).publishPercentileHistogram();
  }

  @Override
  public void controllerRegistered(Controller<? extends HasMetadata> controller) {
    final var configuration = controller.getConfiguration();
    final var name = configuration.getName();
    final var executingThreadsRefName = reconciliationExecutionGaugeRefKey(name);
    final var tags = new ArrayList<Tag>();
    addControllerNameTag(name, tags);
    AtomicInteger executingThreads =
        registry.gauge(RECONCILIATIONS_EXECUTIONS_GAUGE, tags, new AtomicInteger(0));
    gauges.put(executingThreadsRefName, executingThreads);

    final var controllerQueueRefName = controllerQueueSizeGaugeRefKey(name);
    AtomicInteger controllerQueueSize =
        registry.gauge(RECONCILIATIONS_QUEUE_SIZE_GAUGE, tags, new AtomicInteger(0));
    gauges.put(controllerQueueRefName, controllerQueueSize);

    var numberOfResources = registry.gauge(NUMBER_OF_RESOURCE_GAUGE, tags, new AtomicInteger(0));
    gauges.put(numberOfResourcesRefName(name), numberOfResources);

    final var timerBuilder = Timer.builder(RECONCILIATION_EXECUTION_DURATION).tags(tags);
    timerConfig.accept(timerBuilder);
    var timer = timerBuilder.register(registry);
    executionTimers.put(name, timer);
  }

  private String numberOfResourcesRefName(String name) {
    return NUMBER_OF_RESOURCE_GAUGE + name;
  }

  // todo does it make sense to have both controller and reconciler execution counters?
  @Override
  public <T> T timeControllerExecution(ControllerExecution<T> execution) {
    final var name = execution.controllerName();
    final var tags = new ArrayList<Tag>(1);
    addControllerNameTag(name, tags);

    final var timer = executionTimers.get(name);
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
      registry.counter(CONTROLLERS_SUCCESSFUL_EXECUTION, CONTROLLER_NAME, name).increment();
      return result;
    } catch (Exception e) {
      registry.counter(CONTROLLERS_FAILED_EXECUTION, CONTROLLER_NAME, name).increment();
      throw e;
    }
  }

  @Override
  public void receivedEvent(Event event, Map<String, Object> metadata) {
    if (event instanceof ResourceEvent resourceEvent) {
      if (resourceEvent.getAction() == ResourceAction.ADDED) {
        gauges.get(numberOfResourcesRefName(getControllerName(metadata))).incrementAndGet();
      }
      if (resourceEvent.getAction() == ResourceAction.DELETED) {
        gauges.get(numberOfResourcesRefName(getControllerName(metadata))).decrementAndGet();
      }
      incrementCounter(
          EVENTS_RECEIVED,
          metadata,
          Tag.of(EVENT, event.getClass().getSimpleName()),
          Tag.of(ACTION, resourceEvent.getAction().toString()));
    } else {
      incrementCounter(
          EVENTS_RECEIVED,
          metadata,
          Tag.of(EVENT, event.getClass().getSimpleName()),
          Tag.of(ACTION, UNKNOWN_ACTION));
    }
  }

  @Override
  public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {
    incrementCounter(EVENTS_DELETE, metadata);
  }

  @Override
  public void submittedForReconciliation(
      HasMetadata resource, RetryInfo retryInfoNullable, Map<String, Object> metadata) {
    Optional<RetryInfo> retryInfo = Optional.ofNullable(retryInfoNullable);

    // Record the counter without retry tags
    incrementCounter(RECONCILIATIONS_STARTED, metadata);

    int retryNumber = retryInfo.map(RetryInfo::getAttemptCount).orElse(0);
    if (retryNumber > 0) {
      incrementCounter(RECONCILIATIONS_RETRIES_NUMBER, metadata);
    }

    var controllerQueueSize =
        gauges.get(controllerQueueSizeGaugeRefKey(getControllerName(metadata)));
    controllerQueueSize.incrementAndGet();
  }

  @Override
  public void successfullyFinishedReconciliation(
      HasMetadata resource, Map<String, Object> metadata) {
    incrementCounter(RECONCILIATIONS_SUCCESS, metadata);
  }

  @Override
  public void reconciliationExecutionStarted(HasMetadata resource, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(reconciliationExecutionGaugeRefKey(getControllerName(metadata)));
    reconcilerExecutions.incrementAndGet();
  }

  @Override
  public void reconciliationExecutionFinished(
      HasMetadata resource, RetryInfo retryInfo, Map<String, Object> metadata) {
    var reconcilerExecutions =
        gauges.get(reconciliationExecutionGaugeRefKey(metadata.get(CONTROLLER_NAME).toString()));
    reconcilerExecutions.decrementAndGet();

    var controllerQueueSize =
        gauges.get(controllerQueueSizeGaugeRefKey(metadata.get(CONTROLLER_NAME).toString()));
    controllerQueueSize.decrementAndGet();
  }

  @Override
  public void failedReconciliation(
      HasMetadata resource, RetryInfo retry, Exception exception, Map<String, Object> metadata) {
    incrementCounter(RECONCILIATIONS_FAILED, metadata);
  }

  private static void addTag(String name, String value, List<Tag> tags) {
    tags.add(Tag.of(name, value));
  }

  private static void addControllerNameTag(Map<String, Object> metadata, List<Tag> tags) {
    addTag(CONTROLLER_NAME, getControllerName(metadata), tags);
  }

  private static void addControllerNameTag(String name, List<Tag> tags) {
    addTag(CONTROLLER_NAME, name, tags);
  }

  private void incrementCounter(
      String counterName, Map<String, Object> metadata, Tag... additionalTags) {

    final var tags = new ArrayList<Tag>(1 + additionalTags.length);
    addControllerNameTag(metadata, tags);
    if (additionalTags.length > 0) {
      tags.addAll(List.of(additionalTags));
    }
    registry.counter(counterName, tags).increment();
  }

  private static @NonNull String reconciliationExecutionGaugeRefKey(String controllerName) {
    return RECONCILIATIONS_EXECUTIONS_GAUGE + "." + controllerName;
  }

  private static @NonNull String controllerQueueSizeGaugeRefKey(String controllerName) {
    return RECONCILIATIONS_QUEUE_SIZE_GAUGE + "." + controllerName;
  }

  public static String getControllerName(Map<String, Object> metadata) {
    return (String) metadata.get(Constants.CONTROLLER_NAME);
  }

  public static class MicrometerMetricsV2Builder {
    protected final MeterRegistry registry;
    protected Consumer<Timer.Builder> executionTimerConfig = null;

    public MicrometerMetricsV2Builder(MeterRegistry registry) {
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
    public MicrometerMetricsV2Builder withExecutionTimerConfig(
        Consumer<Timer.Builder> executionTimerConfig) {
      this.executionTimerConfig = executionTimerConfig;
      return this;
    }

    public MicrometerMetricsV2 build() {
      return new MicrometerMetricsV2(registry, executionTimerConfig);
    }
  }
}
