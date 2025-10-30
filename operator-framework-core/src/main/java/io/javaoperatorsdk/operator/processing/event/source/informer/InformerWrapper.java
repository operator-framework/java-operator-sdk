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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ExceptionHandler;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

class InformerWrapper<T extends HasMetadata>
    implements LifecycleAware, IndexerResourceCache<T>, InformerHealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(InformerWrapper.class);

  private final SharedIndexInformer<T> informer;
  private final Cache<T> cache;
  private final String namespaceIdentifier;
  private final ConfigurationService configurationService;

  public InformerWrapper(
      SharedIndexInformer<T> informer,
      ConfigurationService configurationService,
      String namespaceIdentifier) {
    this.informer = informer;
    this.namespaceIdentifier = namespaceIdentifier;
    this.cache = (Cache<T>) informer.getStore();
    this.configurationService = configurationService;
  }

  @Override
  public void start() throws OperatorException {
    try {

      // register stopped handler if we have one defined
      configurationService
          .getInformerStoppedHandler()
          .ifPresent(
              ish -> {
                final var stopped = informer.stopped();
                if (stopped != null) {
                  stopped.handle(
                      (res, ex) -> {
                        ish.onStop(informer, ex);
                        return null;
                      });
                } else {
                  final var apiTypeClass = informer.getApiTypeClass();
                  final var fullResourceName = HasMetadata.getFullResourceName(apiTypeClass);
                  final var version = HasMetadata.getVersion(apiTypeClass);
                  throw new IllegalStateException(
                      "Cannot retrieve 'stopped' callback to listen to informer stopping for"
                          + " informer for "
                          + fullResourceName
                          + "/"
                          + version);
                }
              });
      if (!configurationService.stopOnInformerErrorDuringStartup()) {
        informer.exceptionHandler((b, t) -> !ExceptionHandler.isDeserializationException(t));
      }
      // change thread name for easier debugging
      final var thread = Thread.currentThread();
      final var name = thread.getName();
      try {
        thread.setName(informerInfo() + " " + thread.getId());
        final var resourceName = informer.getApiTypeClass().getSimpleName();
        log.debug(
            "Starting informer for namespace: {} resource: {}", namespaceIdentifier, resourceName);
        var start = informer.start();
        // note that in case we don't put here timeout and stopOnInformerErrorDuringStartup is
        // false, and there is a rbac issue the get never returns; therefore operator never really
        // starts
        log.trace(
            "Waiting informer to start namespace: {} resource: {}",
            namespaceIdentifier,
            resourceName);
        start
            .toCompletableFuture()
            .get(configurationService.cacheSyncTimeout().toMillis(), TimeUnit.MILLISECONDS);
        log.debug(
            "Started informer for namespace: {} resource: {}", namespaceIdentifier, resourceName);
      } catch (TimeoutException | ExecutionException e) {
        if (configurationService.stopOnInformerErrorDuringStartup()) {
          log.error("Informer startup error. Operator will be stopped. Informer: {}", informer, e);
          throw new OperatorException(e);
        } else {
          log.warn("Informer startup error. Will periodically retry. Informer: {}", informer, e);
        }
      } catch (InterruptedException e) {
        thread.interrupt();
        throw new IllegalStateException(e);
      } finally {
        // restore original name
        thread.setName(name);
      }

    } catch (Exception e) {
      ReconcilerUtils.handleKubernetesClientException(
          e, HasMetadata.getFullResourceName(informer.getApiTypeClass()));
      throw new OperatorException(
          "Couldn't start informer for " + versionedFullResourceName() + " resources", e);
    }
  }

  private String versionedFullResourceName() {
    final var apiTypeClass = informer.getApiTypeClass();
    if (apiTypeClass.isAssignableFrom(GenericKubernetesResource.class)) {
      return GenericKubernetesResource.class.getSimpleName();
    }
    return ReconcilerUtils.getResourceTypeNameWithVersion(apiTypeClass);
  }

  @Override
  public void stop() throws OperatorException {
    informer.stop();
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return Optional.ofNullable(cache.getByKey(getKey(resourceID)));
  }

  public String getLastSyncResourceVersion() {
    return this.informer.lastSyncResourceVersion();
  }

  private String getKey(ResourceID resourceID) {
    return Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null), resourceID.getName());
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return cache.list().stream().filter(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    final var stream =
        cache.list().stream().filter(r -> namespace.equals(r.getMetadata().getNamespace()));
    return predicate != null ? stream.filter(predicate) : stream;
  }

  @Override
  public Stream<ResourceID> keys() {
    return cache.listKeys().stream().map(Mappers::fromString);
  }

  public void addEventHandler(ResourceEventHandler<T> eventHandler) {
    informer.addEventHandler(eventHandler);
  }

  @Override
  public void addIndexers(Map<String, Function<T, List<String>>> indexers) {
    informer.getIndexer().addIndexers(indexers);
  }

  @Override
  public List<T> byIndex(String indexName, String indexKey) {
    return informer.getIndexer().byIndex(indexName, indexKey);
  }

  @Override
  public String toString() {
    return informerInfo() + " (" + informer + ')';
  }

  private String informerInfo() {
    return "InformerWrapper [" + versionedFullResourceName() + "]";
  }

  @Override
  public boolean hasSynced() {
    return informer.hasSynced();
  }

  @Override
  public boolean isWatching() {
    return informer.isWatching();
  }

  @Override
  public boolean isRunning() {
    return informer.isRunning();
  }

  @Override
  public Status getStatus() {
    var status = isRunning() && hasSynced() && isWatching() ? Status.HEALTHY : Status.UNHEALTHY;
    log.debug(
        "Informer status: {} for type: {}, namespace: {}, details [is running: {}, has synced: {},"
            + " is watching: {}]",
        status,
        informer.getApiTypeClass().getSimpleName(),
        namespaceIdentifier,
        isRunning(),
        hasSynced(),
        isWatching());
    return status;
  }

  @Override
  public String getTargetNamespace() {
    return namespaceIdentifier;
  }
}
