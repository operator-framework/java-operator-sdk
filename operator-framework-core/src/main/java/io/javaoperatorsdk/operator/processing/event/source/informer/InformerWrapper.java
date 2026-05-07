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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.OperatorException;
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

  public InformerWrapper(SharedIndexInformer<T> informer, String namespaceIdentifier) {
    this.informer = informer;
    this.namespaceIdentifier = namespaceIdentifier;
    this.cache = (Cache<T>) informer.getStore();
  }

  @Override
  public void start() {
    // no-op: informer initialization is handled by InformerPool
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
    return "InformerWrapper [" + informer.getApiTypeClass().getSimpleName() + "]";
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
    var status = isRunning() && hasSynced() ? Status.HEALTHY : Status.UNHEALTHY;
    log.debug(
        "Informer status: {} for type: {}, namespace: {}, details [is running: {}, has synced: {}]",
        status,
        informer.getApiTypeClass().getSimpleName(),
        namespaceIdentifier,
        isRunning(),
        hasSynced());
    return status;
  }

  @Override
  public String getTargetNamespace() {
    return namespaceIdentifier;
  }
}
