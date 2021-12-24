package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
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
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

public class InformerManager<T extends HasMetadata, C extends ResourceConfiguration<T>>
    implements LifecycleAware, ResourceCache<T>, UpdatableCache<T> {

  private static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";
  private static final Logger log = LoggerFactory.getLogger(InformerManager.class);

  private final Map<String, InformerWrapper<T>> sources = new ConcurrentHashMap<>();
  private Cloner cloner;

  @Override
  public void start() throws OperatorException {
    sources.values().parallelStream().forEach(LifecycleAware::start);
  }

  void initSources(MixedOperation<T, KubernetesResourceList<T>, Resource<T>> client,
      C configuration, ResourceEventHandler<T> eventHandler) {
    final var service = configuration.getConfigurationService();
    cloner = service == null ? ConfigurationService.DEFAULT_CLONER : service.getResourceCloner();

    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var labelSelector = configuration.getLabelSelector();

    if (ResourceConfiguration.allNamespacesWatched(targetNamespaces)) {
      final var filteredBySelectorClient =
          client.inAnyNamespace().withLabelSelector(labelSelector);
      final var source =
          createEventSource(filteredBySelectorClient, eventHandler, ANY_NAMESPACE_MAP_KEY);
      log.debug("Registered {} -> {} for any namespace", this, source);
    } else {
      targetNamespaces.forEach(
          ns -> {
            final var source =
                createEventSource(client.inNamespace(ns).withLabelSelector(labelSelector),
                    eventHandler, ns);
            log.debug("Registered {} -> {} for namespace: {}", this, source,
                ns);
          });
    }
  }


  private InformerWrapper<T> createEventSource(
      FilterWatchListDeletable<T, KubernetesResourceList<T>> filteredBySelectorClient,
      ResourceEventHandler<T> eventHandler, String key) {
    var source = new InformerWrapper<>(filteredBySelectorClient.runnableInformer(0));
    source.addEventHandler(eventHandler);
    sources.put(key, source);
    return source;
  }

  @Override
  public void stop() {
    for (InformerWrapper<T> source : sources.values()) {
      try {
        log.info("Stopping informer {} -> {}", this, source);
        source.stop();
      } catch (Exception e) {
        log.warn("Error stopping informer {} -> {}", this, source, e);
      }
    }
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    if (predicate == null) {
      return sources.values().stream().flatMap(ResourceCache::list);
    }
    return sources.values().stream().flatMap(i -> i.list(predicate));
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    if (isWatchingAllNamespaces()) {
      return getSource(ANY_NAMESPACE_MAP_KEY)
          .map(source -> source.list(namespace, predicate))
          .orElse(Stream.empty());
    } else {
      return getSource(namespace)
          .map(source -> source.list(predicate))
          .orElse(Stream.empty());
    }
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return getSource(resourceID.getNamespace().orElse(ANY_NAMESPACE_MAP_KEY))
        .flatMap(source -> source.get(resourceID))
        .map(cloner::clone);
  }

  @Override
  public Stream<ResourceID> keys() {
    return sources.values().stream().flatMap(Cache::keys);
  }

  private boolean isWatchingAllNamespaces() {
    return sources.containsKey(ANY_NAMESPACE_MAP_KEY);
  }

  private Optional<InformerWrapper<T>> getSource(String namespace) {
    namespace = isWatchingAllNamespaces() || namespace == null ? ANY_NAMESPACE_MAP_KEY : namespace;
    return Optional.ofNullable(sources.get(namespace));
  }

  @Override
  public T remove(ResourceID key) {
    return getSource(key.getNamespace().orElse(ANY_NAMESPACE_MAP_KEY))
        .map(c -> c.remove(key))
        .orElse(null);
  }

  @Override
  public void put(ResourceID key, T resource) {
    getSource(key.getNamespace().orElse(ANY_NAMESPACE_MAP_KEY))
        .ifPresent(c -> c.put(key, resource));
  }
}
