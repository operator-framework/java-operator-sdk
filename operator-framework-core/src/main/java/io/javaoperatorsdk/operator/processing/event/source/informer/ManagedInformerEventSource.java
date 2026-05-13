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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Informable;
import io.javaoperatorsdk.operator.api.config.NamespaceChangeable;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.health.InformerWrappingEventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.*;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceDeleteEvent;

@SuppressWarnings("rawtypes")
public abstract class ManagedInformerEventSource<
        R extends HasMetadata, P extends HasMetadata, C extends Informable<R>>
    extends AbstractEventSource<R, P>
    implements ResourceEventHandler<R>,
        Cache<R>,
        IndexerResourceCache<R>,
        RecentOperationCacheFiller<R>,
        NamespaceChangeable,
        InformerWrappingEventSourceHealthIndicator<R>,
        Configurable<C> {

  private static final Logger log = LoggerFactory.getLogger(ManagedInformerEventSource.class);
  private InformerManager<R, C> cache;
  private final boolean comparableResourceVersions;
  private ControllerConfiguration<R> controllerConfiguration;
  private final C configuration;
  private final Map<String, Function<R, List<String>>> indexers = new HashMap<>();
  protected TemporaryResourceCache<R> temporaryResourceCache;
  protected MixedOperation client;

  protected ManagedInformerEventSource(String name, MixedOperation client, C configuration) {
    super(configuration.getResourceClass(), name);
    this.comparableResourceVersions =
        configuration.getInformerConfig().isComparableResourceVersions();
    this.client = client;
    this.configuration = configuration;
  }

  protected InformerManager<R, C> manager() {
    return cache;
  }

  @Override
  public void changeNamespaces(Set<String> namespaces) {
    if (allowsNamespaceChanges()) {
      manager().changeNamespaces(namespaces);
    }
  }

  /**
   * Updates the resource and makes sure that the response is available for the next reconciliation.
   * Also makes sure that the even produced by this update is filtered, thus does not trigger the
   * reconciliation.
   */
  @SuppressWarnings("unchecked")
  public R eventFilteringUpdateAndCacheResource(R resourceToUpdate, UnaryOperator<R> updateMethod) {
    ResourceID id = ResourceID.fromResource(resourceToUpdate);
    log.debug("Starting event filtering and caching update");
    R updatedResource = null;
    try {
      temporaryResourceCache.startEventFilteringModify(id);
      updatedResource = updateMethod.apply(resourceToUpdate);
      log.debug("Resource update successful");
      handleRecentResourceUpdate(id, updatedResource, resourceToUpdate);
      return updatedResource;
    } finally {
      var res =
          temporaryResourceCache.doneEventFilterModify(
              id,
              updatedResource == null ? null : updatedResource.getMetadata().getResourceVersion());
      var updatedForLambda = updatedResource;
      res.ifPresentOrElse(
          r -> {
            R latestResource = (R) r.getResource().orElseThrow();
            // as previous resource version we use the one from successful update, since
            // we process new event here only if that is more recent then the event from our update.
            // Note that this is equivalent with the scenario when an informer watch connection
            // would reconnect and loose some events in between.
            // If that update was not successful we still record the previous version from the
            // actual event in the ExtendedResourceEvent.
            R extendedResourcePrevVersion =
                (r instanceof ExtendedResourceEvent)
                    ? (R) ((ExtendedResourceEvent) r).getPreviousResource().orElse(null)
                    : null;
            R prevVersionOfResource =
                updatedForLambda != null ? updatedForLambda : extendedResourcePrevVersion;
            if (log.isDebugEnabled()) {
              log.debug(
                  "Previous resource version: {} resource from update present: {}"
                      + " extendedPrevResource present: {}",
                  prevVersionOfResource.getMetadata().getResourceVersion(),
                  updatedForLambda != null,
                  extendedResourcePrevVersion != null);
            }
            handleEvent(
                r.getAction(),
                latestResource,
                prevVersionOfResource,
                (r instanceof ResourceDeleteEvent)
                    ? ((ResourceDeleteEvent) r).isDeletedFinalStateUnknown()
                    : null);
          },
          () -> log.debug("No new event present after the filtering update"));
    }
  }

  protected abstract void handleEvent(
      ResourceAction action, R resource, R oldResource, Boolean deletedFinalStateUnknown);

  @SuppressWarnings("unchecked")
  @Override
  public synchronized void start() {
    if (isRunning()) {
      return;
    }
    temporaryResourceCache = new TemporaryResourceCache<>(comparableResourceVersions, this);
    this.cache = new InformerManager<>(client, configuration, this);
    cache.setControllerConfiguration(controllerConfiguration);
    cache.addIndexers(indexers);
    manager().start();
    super.start();
  }

  @Override
  public synchronized void stop() {
    if (!isRunning()) {
      return;
    }
    super.stop();
    manager().stop();
  }

  @Override
  public void onList(String resourceVersion, boolean remainedEmpty) {
    temporaryResourceCache.checkGhostResources();
  }

  @Override
  public void handleRecentResourceUpdate(
      ResourceID resourceID, R resource, R previousVersionOfResource) {
    temporaryResourceCache.putResource(resource);
  }

  @Override
  public void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    temporaryResourceCache.putResource(resource);
  }

  @Override
  public Optional<R> get(ResourceID resourceID) {
    // The order of reading from these caches matters
    Optional<R> resource = temporaryResourceCache.getResourceFromCache(resourceID);
    if (comparableResourceVersions
        && resource.isPresent()
        && ReconcilerUtilsInternal.compareResourceVersions(
                resource.get().getMetadata().getResourceVersion(),
                manager().lastSyncResourceVersion(resource.get().getMetadata().getNamespace()))
            > 0) {
      log.debug("Latest resource found in temporary cache for Resource ID: {}", resourceID);
      return resource;
    } else {
      // this needs to happen after comparison to ensure correctness
      var resFromInformer = cache.get(resourceID);
      log.debug(
          "Resource {} in temp cache; {}found in informer cache" + ", for Resource ID: {}",
          resource.isPresent() ? "obsolete" : "not found",
          resFromInformer.isPresent() ? "" : "not ",
          resourceID);
      return resFromInformer;
    }
  }

  /**
   * @deprecated Use {@link #get(ResourceID)} instead.
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public Optional<R> getCachedValue(ResourceID resourceID) {
    return get(resourceID);
  }

  void setTemporalResourceCache(TemporaryResourceCache<R> temporaryResourceCache) {
    this.temporaryResourceCache = temporaryResourceCache;
  }

  @Override
  public void addIndexers(Map<String, Function<R, List<String>>> indexers) {
    if (isRunning()) {
      throw new OperatorException("Cannot add indexers after InformerEventSource started.");
    }
    this.indexers.putAll(indexers);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation is read-cache-after-write consistent. Results are merged with the
   * temporary resource cache to ensure recently written resources are reflected in the output.
   */
  @Override
  public Stream<R> list(String namespace, Predicate<R> predicate) {
    return mergeWithTempCacheForList(manager().list(namespace), namespace, predicate);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation is read-cache-after-write consistent. Results are merged with the
   * temporary resource cache to ensure recently written resources are reflected in the output.
   */
  @Override
  public Stream<R> list(Predicate<R> predicate) {
    return mergeWithTempCacheForList(manager().list(), null, predicate);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation is read-cache-after-write consistent. Results are merged with the
   * temporary resource cache to ensure recently written resources are reflected in the output.
   */
  @Override
  public Stream<R> byIndexStream(String indexName, String indexKey) {
    return mergeWithTempCacheForIndex(
        manager().byIndexStream(indexName, indexKey), indexName, indexKey);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation is read-cache-after-write consistent. Results are merged with the
   * temporary resource cache to ensure recently written resources are reflected in the output.
   */
  @Override
  public List<R> byIndex(String indexName, String indexKey) {
    return mergeWithTempCacheForIndex(
            manager().byIndexStream(indexName, indexKey), indexName, indexKey)
        .collect(Collectors.toList());
  }

  // namespace is filtered on informer manager level
  private Stream<R> mergeWithTempCacheForList(
      Stream<R> stream, String namespace, Predicate<R> predicate) {
    if (!comparableResourceVersions || temporaryResourceCache.isEmpty()) {

      return stream.filter(filterResourceByPredicate(predicate));
    }
    var tempResources = temporaryResourceCache.getResources();
    if (tempResources.isEmpty()) {
      return stream.filter(filterResourceByPredicate(predicate));
    }

    var upToDateList =
        stream
            .map(
                r -> {
                  var resourceID = ResourceID.fromResource(r);
                  var tempResource = tempResources.remove(resourceID);
                  if (tempResource != null
                      && ReconcilerUtilsInternal.compareResourceVersions(tempResource, r) > 0) {
                    return tempResource;
                  }
                  return r;
                })
            // we filter on predicate only since namespace changes would not be detected anyway.
            .filter(filterResourceByPredicate(predicate))
            .toList();

    return Stream.concat(
        tempResources.values().stream()
            .filter(filterResourceByNamespaceAndPredicate(namespace, predicate)),
        upToDateList.stream());
  }

  private Stream<R> mergeWithTempCacheForIndex(
      Stream<R> stream, String indexName, String indexKey) {
    if (!comparableResourceVersions || temporaryResourceCache.isEmpty()) {
      return stream;
    }
    var tempResources = temporaryResourceCache.getResources();
    if (tempResources.isEmpty()) {
      return stream;
    }

    var indexer = indexers.get(indexName);
    if (indexer == null) {
      throw new IllegalArgumentException("Indexer not found for: " + indexName);
    }

    var upToDateList =
        stream
            .map(
                r -> {
                  var resourceID = ResourceID.fromResource(r);
                  var tempResource = tempResources.remove(resourceID);
                  if (tempResource != null
                      && ReconcilerUtilsInternal.compareResourceVersions(tempResource, r) > 0) {
                    if (!indexer.apply(tempResource).contains(indexKey)) {
                      return null;
                    }
                    return tempResource;
                  }
                  return r;
                })
            .filter(Objects::nonNull)
            .toList();

    // remaining temp resources are ghost resources — include only those matching the index
    return Stream.concat(
        tempResources.values().stream().filter(r -> indexer.apply(r).contains(indexKey)),
        upToDateList.stream());
  }

  private static <R extends HasMetadata> Predicate<R> filterResourceByPredicate(
      Predicate<R> predicate) {
    return filterResourceByNamespaceAndPredicate(null, predicate);
  }

  private static <R extends HasMetadata> Predicate<R> filterResourceByNamespaceAndPredicate(
      String namespace, Predicate<R> predicate) {
    return r -> {
      if (namespace != null) {
        if (!Optional.of(r)
            .map(rr -> Objects.equals(namespace, rr.getMetadata().getNamespace()))
            .orElse(false)) {
          return false;
        }
      }
      if (predicate != null) {
        return predicate.test(r);
      }
      return true;
    };
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation is read-cache-after-write consistent. Keys from the temporary resource
   * cache (ghost resources) are included in the result.
   */
  @Override
  public Stream<ResourceID> keys() {
    if (!comparableResourceVersions || temporaryResourceCache.isEmpty()) {
      return manager().keys();
    }
    var managerKeys = manager().keys().collect(Collectors.toSet());
    var tempKeys = temporaryResourceCache.getResources().keySet();
    return Stream.concat(
        managerKeys.stream(), tempKeys.stream().filter(k -> !managerKeys.contains(k)));
  }

  @Override
  public Map<String, InformerHealthIndicator> informerHealthIndicators() {
    return cache.informerHealthIndicators();
  }

  @Override
  public Status getStatus() {
    return InformerWrappingEventSourceHealthIndicator.super.getStatus();
  }

  @Override
  public C configuration() {
    return configuration;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "resourceClass: "
        + configuration().getResourceClass().getSimpleName()
        + "}";
  }

  public void setControllerConfiguration(ControllerConfiguration<R> controllerConfiguration) {
    this.controllerConfiguration = controllerConfiguration;
  }

  protected void withMDC(R resource, ResourceAction action, Runnable runnable) {
    MDCUtils.withMDCForEvent(resource, action, runnable, name());
  }
}
