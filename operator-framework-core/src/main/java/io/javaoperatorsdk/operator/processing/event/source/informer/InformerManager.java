package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

public class InformerManager<T extends HasMetadata, C extends ResourceConfiguration<T>>
    implements LifecycleAware, IndexerResourceCache<T> {

  private static final Logger log = LoggerFactory.getLogger(InformerManager.class);

  private final Map<String, InformerWrapper<T>> sources = new ConcurrentHashMap<>();
  private Cloner cloner;
  private final C configuration;
  private final MixedOperation<T, KubernetesResourceList<T>, Resource<T>> client;
  private final ResourceEventHandler<T> eventHandler;
  private final Map<String, Function<T, List<String>>> indexers = new HashMap<>();

  public InformerManager(MixedOperation<T, KubernetesResourceList<T>, Resource<T>> client,
      C configuration,
      ResourceEventHandler<T> eventHandler) {
    this.client = client;
    this.configuration = configuration;
    this.eventHandler = eventHandler;
  }

  @Override
  public void start() throws OperatorException {
    initSources();
    // make sure informers are all started before proceeding further
    sources.values().parallelStream().forEach(InformerWrapper::start);
  }

  private void initSources() {
    if (!sources.isEmpty()) {
      throw new IllegalStateException("Some sources already initialized.");
    }
    cloner = ConfigurationServiceProvider.instance().getResourceCloner();
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    if (ResourceConfiguration.allNamespacesWatched(targetNamespaces)) {
      var source = createEventSourceForNamespace(WATCH_ALL_NAMESPACES);
      log.debug("Registered {} -> {} for any namespace", this, source);
    } else {
      targetNamespaces.forEach(
          ns -> {
            final var source = createEventSourceForNamespace(ns);
            log.debug("Registered {} -> {} for namespace: {}", this, source,
                ns);
          });
    }
  }

  C configuration() {
    return configuration;
  }

  public void changeNamespaces(Set<String> namespaces) {
    var sourcesToRemove = sources.keySet().stream()
        .filter(k -> !namespaces.contains(k)).collect(Collectors.toSet());
    log.debug("Stopped informer {} for namespaces: {}", this, sourcesToRemove);
    sourcesToRemove.forEach(k -> sources.remove(k).stop());

    namespaces.forEach(ns -> {
      if (!sources.containsKey(ns)) {
        final InformerWrapper<T> source = createEventSourceForNamespace(ns);
        source.start();
        log.debug("Registered new {} -> {} for namespace: {}", this, source,
            ns);
      }
    });
  }


  private InformerWrapper<T> createEventSourceForNamespace(String namespace) {
    final InformerWrapper<T> source;
    if (namespace.equals(WATCH_ALL_NAMESPACES)) {
      final var filteredBySelectorClient =
          client.inAnyNamespace().withLabelSelector(configuration.getLabelSelector());
      source = createEventSource(filteredBySelectorClient, eventHandler, WATCH_ALL_NAMESPACES);
    } else {
      source = createEventSource(
          client.inNamespace(namespace).withLabelSelector(configuration.getLabelSelector()),
          eventHandler, namespace);
    }
    source.addIndexers(indexers);
    return source;
  }

  private InformerWrapper<T> createEventSource(
      FilterWatchListDeletable<T, KubernetesResourceList<T>, Resource<T>> filteredBySelectorClient,
      ResourceEventHandler<T> eventHandler, String namespaceIdentifier) {
    var informer = filteredBySelectorClient.runnableInformer(0);
    var source =
        new InformerWrapper<>(informer, namespaceIdentifier);
    source.addEventHandler(eventHandler);
    sources.put(namespaceIdentifier, source);
    return source;
  }

  @Override
  public void stop() {
    sources.forEach((ns, source) -> {
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
  public Stream<T> list(Predicate<T> predicate) {
    if (predicate == null) {
      return sources.values().stream().flatMap(IndexerResourceCache::list);
    }
    return sources.values().stream().flatMap(i -> i.list(predicate));
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    if (isWatchingAllNamespaces()) {
      return getSource(WATCH_ALL_NAMESPACES)
          .map(source -> source.list(namespace, predicate))
          .orElseGet(Stream::empty);
    } else {
      return getSource(namespace)
          .map(source -> source.list(predicate))
          .orElseGet(Stream::empty);
    }
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return getSource(resourceID.getNamespace().orElse(WATCH_ALL_NAMESPACES))
        .flatMap(source -> source.get(resourceID))
        .map(cloner::clone);
  }

  @Override
  public Stream<ResourceID> keys() {
    return sources.values().stream().flatMap(Cache::keys);
  }

  private boolean isWatchingAllNamespaces() {
    return sources.containsKey(WATCH_ALL_NAMESPACES);
  }

  private Optional<InformerWrapper<T>> getSource(String namespace) {
    namespace = isWatchingAllNamespaces() || namespace == null ? WATCH_ALL_NAMESPACES : namespace;
    return Optional.ofNullable(sources.get(namespace));
  }

  @Override
  public void addIndexers(Map<String, Function<T, List<String>>> indexers) {
    this.indexers.putAll(indexers);
  }

  @Override
  public List<T> byIndex(String indexName, String indexKey) {
    return sources.values().stream().map(s -> s.byIndex(indexName, indexKey))
        .flatMap(List::stream).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    final var selector = configuration.getLabelSelector();
    return "InformerManager ["
        + ReconcilerUtils.getResourceTypeNameWithVersion(configuration.getResourceClass())
        + "] watching: "
        + configuration.getEffectiveNamespaces()
        + (selector != null ? " selector: " + selector : "");
  }

  public Map<String, InformerHealthIndicator> informerHealthIndicators() {
    return Collections.unmodifiableMap(sources);
  }
}
