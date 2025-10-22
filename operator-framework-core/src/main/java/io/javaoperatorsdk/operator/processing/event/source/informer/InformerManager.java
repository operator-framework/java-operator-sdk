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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Informable;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

class InformerManager<R extends HasMetadata, C extends Informable<R>>
    implements LifecycleAware, IndexerResourceCache<R> {

  private static final Logger log = LoggerFactory.getLogger(InformerManager.class);

  private final Map<String, InformerWrapper<R>> sources = new ConcurrentHashMap<>();
  private final C configuration;
  private final MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client;
  private final ResourceEventHandler<R> eventHandler;
  private final Map<String, Function<R, List<String>>> indexers = new HashMap<>();
  private ControllerConfiguration<R> controllerConfiguration;

  InformerManager(
      MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client,
      C configuration,
      ResourceEventHandler<R> eventHandler) {
    this.client = client;
    this.configuration = configuration;
    this.eventHandler = eventHandler;
  }

  void setControllerConfiguration(ControllerConfiguration<R> controllerConfiguration) {
    this.controllerConfiguration = controllerConfiguration;
  }

  @Override
  public void start() throws OperatorException {
    initSources();
    // make sure informers are all started before proceeding further
    controllerConfiguration
        .getConfigurationService()
        .getExecutorServiceManager()
        .boundedExecuteAndWaitForAllToComplete(
            sources.values().stream(),
            iw -> {
              iw.start();
              return null;
            },
            iw ->
                "InformerStarter-"
                    + iw.getTargetNamespace()
                    + "-"
                    + configuration.getResourceClass().getSimpleName());
  }

  private void initSources() {
    if (!sources.isEmpty()) {
      throw new IllegalStateException("Some sources already initialized.");
    }
    final var targetNamespaces =
        configuration.getInformerConfig().getEffectiveNamespaces(controllerConfiguration);
    if (InformerConfiguration.allNamespacesWatched(targetNamespaces)) {
      var source = createEventSourceForNamespace(WATCH_ALL_NAMESPACES);
      log.debug("Registered {} -> {} for any namespace", this, source);
    } else {
      targetNamespaces.forEach(
          ns -> {
            final var source = createEventSourceForNamespace(ns);
            log.debug("Registered {} -> {} for namespace: {}", this, source, ns);
          });
    }
  }

  C configuration() {
    return configuration;
  }

  public void changeNamespaces(Set<String> namespaces) {
    var sourcesToRemove =
        sources.keySet().stream().filter(k -> !namespaces.contains(k)).collect(Collectors.toSet());
    log.debug("Stopped informer {} for namespaces: {}", this, sourcesToRemove);
    sourcesToRemove.forEach(k -> sources.remove(k).stop());

    namespaces.forEach(
        ns -> {
          if (!sources.containsKey(ns)) {
            final InformerWrapper<R> source = createEventSourceForNamespace(ns);
            source.start();
            log.debug("Registered new {} -> {} for namespace: {}", this, source, ns);
          }
        });
  }

  private InformerWrapper<R> createEventSourceForNamespace(String namespace) {
    final InformerWrapper<R> source;
    final var labelSelector = configuration.getInformerConfig().getLabelSelector();
    if (namespace.equals(WATCH_ALL_NAMESPACES)) {
      final var filteredBySelectorClient = client.inAnyNamespace().withLabelSelector(labelSelector);
      source = createEventSource(filteredBySelectorClient, eventHandler, WATCH_ALL_NAMESPACES);
    } else {
      source =
          createEventSource(
              client.inNamespace(namespace).withLabelSelector(labelSelector),
              eventHandler,
              namespace);
    }
    source.addIndexers(indexers);
    return source;
  }

  private InformerWrapper<R> createEventSource(
      FilterWatchListDeletable<R, KubernetesResourceList<R>, Resource<R>> filteredBySelectorClient,
      ResourceEventHandler<R> eventHandler,
      String namespaceIdentifier) {
    final var informerConfig = configuration.getInformerConfig();

    if (informerConfig.getFieldSelector() != null
        && !informerConfig.getFieldSelector().getFields().isEmpty()) {
      for (var f : informerConfig.getFieldSelector().getFields()) {
        if (f.negated()) {
          filteredBySelectorClient = filteredBySelectorClient.withoutField(f.path(), f.value());
        } else {
          filteredBySelectorClient = filteredBySelectorClient.withField(f.path(), f.value());
        }
      }
    }

    var informer =
        Optional.ofNullable(informerConfig.getInformerListLimit())
            .map(filteredBySelectorClient::withLimit)
            .orElse(filteredBySelectorClient)
            .runnableInformer(0);
    Optional.ofNullable(informerConfig.getItemStore()).ifPresent(informer::itemStore);
    var source =
        new InformerWrapper<>(
            informer, controllerConfiguration.getConfigurationService(), namespaceIdentifier);
    source.addEventHandler(eventHandler);
    sources.put(namespaceIdentifier, source);
    return source;
  }

  @Override
  public void stop() {
    sources.forEach(
        (ns, source) -> {
          try {
            log.debug("Stopping informer for namespace: {} -> {}", ns, source);
            source.stop();
          } catch (Exception e) {
            log.warn("Error stopping informer for namespace: {} -> {}", ns, source, e);
          }
        });
    sources.clear();
  }

  @Override
  public Stream<R> list(Predicate<R> predicate) {
    if (predicate == null) {
      return sources.values().stream().flatMap(IndexerResourceCache::list);
    }
    return sources.values().stream().flatMap(i -> i.list(predicate));
  }

  @Override
  public Stream<R> list(String namespace, Predicate<R> predicate) {
    if (isWatchingAllNamespaces()) {
      return getSource(WATCH_ALL_NAMESPACES)
          .map(source -> source.list(namespace, predicate))
          .orElseGet(Stream::empty);
    } else {
      return getSource(namespace).map(source -> source.list(predicate)).orElseGet(Stream::empty);
    }
  }

  @Override
  public Optional<R> get(ResourceID resourceID) {
    return getSource(resourceID.getNamespace().orElse(WATCH_ALL_NAMESPACES))
        .flatMap(source -> source.get(resourceID))
        .map(
            r ->
                controllerConfiguration
                        .getConfigurationService()
                        .cloneSecondaryResourcesWhenGettingFromCache()
                    ? controllerConfiguration.getConfigurationService().getResourceCloner().clone(r)
                    : r);
  }

  @Override
  public Stream<ResourceID> keys() {
    return sources.values().stream().flatMap(Cache::keys);
  }

  private boolean isWatchingAllNamespaces() {
    return sources.containsKey(WATCH_ALL_NAMESPACES);
  }

  private Optional<InformerWrapper<R>> getSource(String namespace) {
    namespace = isWatchingAllNamespaces() || namespace == null ? WATCH_ALL_NAMESPACES : namespace;
    return Optional.ofNullable(sources.get(namespace));
  }

  @Override
  public void addIndexers(Map<String, Function<R, List<String>>> indexers) {
    this.indexers.putAll(indexers);
  }

  @Override
  public List<R> byIndex(String indexName, String indexKey) {
    return sources.values().stream()
        .map(s -> s.byIndex(indexName, indexKey))
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  @Override
  public String toString() {
    final var informerConfig = configuration.getInformerConfig();
    final var selector = informerConfig.getLabelSelector();
    return "InformerManager ["
        + ReconcilerUtils.getResourceTypeNameWithVersion(configuration.getResourceClass())
        + "] watching: "
        + informerConfig.getEffectiveNamespaces(controllerConfiguration)
        + (selector != null ? " selector: " + selector : "");
  }

  public Map<String, InformerHealthIndicator> informerHealthIndicators() {
    return Collections.unmodifiableMap(sources);
  }
}
