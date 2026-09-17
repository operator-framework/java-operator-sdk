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
package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;

/**
 * Polls resource (on contrary to {@link PerResourcePollingEventSource}) not per resource bases but
 * instead to calls supplier periodically and independently of the number or state of custom
 * resources managed by the controller. It is called on start (synced). This means that when the
 * reconciler first time executed on startup the first poll already happened before. So if the cache
 * does not contain the target resource it means it is not created yet or was deleted while an
 * operator was not running.
 *
 * <p>Another caveat with this is if the cached object is checked in the reconciler and created
 * since not in the cache it should be manually added to the cache, since it can happen that the
 * reconciler is triggered before the cache is propagated with the new resource from a scheduled
 * execution. See {@link #handleRecentResourceCreate(ResourceID, Object)} and update method. So the
 * generic workflow in reconciler should be:
 *
 * <ul>
 *   <li>Check if the cache contains the resource.
 *   <li>If cache contains the resource reconcile it - compare with target state, update if
 *       necessary
 *   <li>if cache not contains the resource create it.
 *   <li>If the resource was created or updated, put the new version of the resource manually to the
 *       cache.
 * </ul>
 *
 * @param <R> type of the polled resource
 * @param <P> primary resource type
 */
public class PollingEventSource<R, P extends HasMetadata, ID>
    extends ExternalResourceCachingEventSource<R, P, ID> {

  private static final Logger log = LoggerFactory.getLogger(PollingEventSource.class);

  private final GenericResourceFetcher<R> genericResourceFetcher;
  private final Duration period;
  private final AtomicBoolean healthy = new AtomicBoolean(true);
  private final ScheduledExecutorService configuredExecutorService;
  private final ConfigurationService configurationService;
  private final boolean ownsExecutorService;

  private volatile ScheduledExecutorService executorService;
  private volatile ScheduledFuture<?> pollingTask;

  /**
   * Creates an event source polling on the executor the operator shares between all its scheduled
   * tasks, unless the configuration provides one of its own.
   *
   * @param resourceClass the type of the polled resource
   * @param context the context this event source is created for
   * @param config the configuration of the polling
   * @since 5.6.0
   */
  public PollingEventSource(
      Class<R> resourceClass, EventSourceContext<P> context, PollingConfiguration<R, ID> config) {
    this(resourceClass, config, configurationServiceOf(context));
  }

  /**
   * @deprecated use {@link #PollingEventSource(Class, EventSourceContext, PollingConfiguration)}
   *     instead: without a context, and unless the configuration provides an executor, this event
   *     source has to create a thread of its own to poll on instead of using the one the operator
   *     shares between all its scheduled tasks.
   */
  @Deprecated(since = "5.6.0")
  public PollingEventSource(Class<R> resourceClass, PollingConfiguration<R, ID> config) {
    this(resourceClass, config, null);
  }

  private PollingEventSource(
      Class<R> resourceClass,
      PollingConfiguration<R, ID> config,
      ConfigurationService configurationService) {
    super(config.name(), resourceClass, config.resourceIDMapper());
    this.genericResourceFetcher = config.genericResourceFetcher();
    this.period = config.period();
    this.configuredExecutorService = config.executorService();
    this.configurationService = configurationService;
    // when neither the configuration nor the operator provides one, the event source has to create
    // an executor of its own, and is then the one responsible for shutting it down
    this.ownsExecutorService = configuredExecutorService == null && configurationService == null;
  }

  /**
   * The configuration of the operator the event source belongs to, or {@code null} if it doesn't
   * belong to one, which only happens when the event source is used standalone, outside an
   * operator.
   */
  private static ConfigurationService configurationServiceOf(EventSourceContext<?> context) {
    final var controllerConfiguration = context.getControllerConfiguration();
    return controllerConfiguration == null
        ? null
        : controllerConfiguration.getConfigurationService();
  }

  @Override
  public void start() throws OperatorException {
    if (pollingTask != null) {
      return;
    }
    super.start();
    executorService = resolveExecutorService();
    getStateAndFillCache();
    pollingTask =
        executorService.scheduleWithFixedDelay(
            this::poll, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void poll() {
    try {
      if (!isRunning()) {
        log.debug("Event source not yet started. Will not run.");
        return;
      }
      getStateAndFillCache();
      healthy.set(true);
    } catch (Exception e) {
      // Exception is required because of Kotlin
      healthy.set(false);
      log.error("Error during polling.", e);
    }
  }

  /**
   * Resolves the executor to poll on. Note that this happens on every start, and not once at
   * creation time, since the operator shuts its executors down when it is stopped and creates new
   * ones if it is started again.
   */
  private ScheduledExecutorService resolveExecutorService() {
    if (configuredExecutorService != null) {
      return configuredExecutorService;
    }
    if (configurationService != null) {
      return configurationService.getExecutorServiceManager().scheduledExecutorService();
    }
    return Executors.newSingleThreadScheduledExecutor(
        Utils.daemonThreadFactory("josdk-polling-" + name()));
  }

  protected synchronized void getStateAndFillCache() {
    var values = genericResourceFetcher.fetchResources();
    handleResources(values);
  }

  public interface GenericResourceFetcher<R> {
    Map<ResourceID, Set<R>> fetchResources();
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    if (pollingTask != null) {
      // as with the java.util.Timer this replaces, an ongoing poll is left to finish
      pollingTask.cancel(false);
      pollingTask = null;
    }
    if (ownsExecutorService && executorService != null) {
      executorService.shutdownNow();
    }
  }

  @Override
  public Status getStatus() {
    return healthy.get() ? Status.HEALTHY : Status.UNHEALTHY;
  }
}
