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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Informable;
import io.javaoperatorsdk.operator.api.config.NamespaceChangeable;
import io.javaoperatorsdk.operator.api.reconciler.ReconcileUtils;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.health.InformerWrappingEventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.*;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
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

  protected ManagedInformerEventSource(
      String name, MixedOperation client, C configuration, boolean comparableResourceVersions) {
    super(configuration.getResourceClass(), name);
    this.comparableResourceVersions = comparableResourceVersions;
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
  public R eventFilteringUpdateAndCacheResource(R resourceToUpdate, UnaryOperator<R> updateMethod) {
    ResourceID id = ResourceID.fromResource(resourceToUpdate);
    if (log.isDebugEnabled()) {
      log.debug("Update and cache: {}", id);
    }
    HasMetadata[] updatedResource = new HasMetadata[1];
    try {
      temporaryResourceCache.startEventFilteringModify(id);
      updatedResource[0] = updateMethod.apply(resourceToUpdate);
      handleRecentResourceUpdate(id, (R) updatedResource[0], resourceToUpdate);
      return (R) updatedResource[0];
    } finally {
      var res =
          temporaryResourceCache.doneEventFilterModify(
              id,
              updatedResource[0] == null
                  ? null
                  : updatedResource[0].getMetadata().getResourceVersion());

      res.ifPresent(
          r ->
              handleEvent(
                  r.getAction(),
                  (R) r.getResource().orElseThrow(),
                  (R) r.getResource().orElseThrow(), // todo handle this nicer for updates?
                  !(r instanceof ResourceDeleteEvent)
                      || ((ResourceDeleteEvent) r).isDeletedFinalStateUnknown(),
                  false));
    }
  }

  public abstract void handleEvent(
      ResourceAction action,
      R resource,
      R oldResource,
      Boolean deletedFinalStateUnknown,
      boolean filterEvent);

  @SuppressWarnings("unchecked")
  @Override
  public synchronized void start() {
    if (isRunning()) {
      return;
    }
    temporaryResourceCache = new TemporaryResourceCache<>(comparableResourceVersions);
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
    var res = cache.get(resourceID);
    Optional<R> resource = temporaryResourceCache.getResourceFromCache(resourceID);
    if (comparableResourceVersions
        && resource.isPresent()
        && res.filter(
                r ->
                    ReconcileUtils.validateAndCompareResourceVersions(r, resource.orElseThrow())
                        > 0)
            .isEmpty()) {
      log.debug("Latest resource found in temporary cache for Resource ID: {}", resourceID);
      return resource;
    }
    log.debug(
        "Resource not found, or older, in temporary cache. Found in informer cache {}, for"
            + " Resource ID: {}",
        res.isPresent(),
        resourceID);
    return res;
  }

  @SuppressWarnings("unused")
  public Optional<R> getCachedValue(ResourceID resourceID) {
    return get(resourceID);
  }

  @Override
  public Stream<R> list(String namespace, Predicate<R> predicate) {
    return manager().list(namespace, predicate);
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

  @Override
  public List<R> byIndex(String indexName, String indexKey) {
    return manager().byIndex(indexName, indexKey);
  }

  @Override
  public Stream<ResourceID> keys() {
    return cache.keys();
  }

  @Override
  public Stream<R> list(Predicate<R> predicate) {
    return cache.list(predicate);
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
}
