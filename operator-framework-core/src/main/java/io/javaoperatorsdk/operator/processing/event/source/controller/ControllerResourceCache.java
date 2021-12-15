package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource.ANY_NAMESPACE_MAP_KEY;

public class ControllerResourceCache<T extends HasMetadata> implements ResourceCache<T> {

  private final Map<String, SharedIndexInformer<T>> sharedIndexInformers;
  private final Cloner cloner;

  public ControllerResourceCache(Map<String, SharedIndexInformer<T>> sharedIndexInformers,
      Cloner cloner) {
    this.sharedIndexInformers = sharedIndexInformers;
    this.cloner = cloner;
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return sharedIndexInformers.values().stream()
        .flatMap(i -> i.getStore().list().stream().filter(predicate));
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    if (isWatchingAllNamespaces()) {
      final var stream = sharedIndexInformers.get(ANY_NAMESPACE_MAP_KEY).getStore().list().stream()
          .filter(r -> r.getMetadata().getNamespace().equals(namespace));
      return predicate != null ? stream.filter(predicate) : stream;
    } else {
      final var informer = sharedIndexInformers.get(namespace);
      return informer != null ? informer.getStore().list().stream().filter(predicate)
          : Stream.empty();
    }
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    var sharedIndexInformer = sharedIndexInformers.get(ANY_NAMESPACE_MAP_KEY);
    if (sharedIndexInformer == null) {
      sharedIndexInformer =
          sharedIndexInformers.get(resourceID.getNamespace().orElse(ANY_NAMESPACE_MAP_KEY));
    }
    var resource = sharedIndexInformer.getStore()
        .getByKey(Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null),
            resourceID.getName()));
    if (resource == null) {
      return Optional.empty();
    } else {
      return Optional.of(cloner.clone(resource));
    }
  }

  private boolean isWatchingAllNamespaces() {
    return sharedIndexInformers.containsKey(ANY_NAMESPACE_MAP_KEY);
  }

}
