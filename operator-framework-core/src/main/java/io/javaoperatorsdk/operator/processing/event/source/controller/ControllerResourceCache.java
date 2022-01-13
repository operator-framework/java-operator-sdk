package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ControllerResourceCache<T extends HasMetadata> implements ResourceCache<T> {

  private final SharedIndexInformer<T> sharedIndexInformer;
  private final Cloner cloner;

  public ControllerResourceCache(SharedIndexInformer<T> sharedIndexInformer, Cloner cloner) {
    this.sharedIndexInformer = sharedIndexInformer;
    this.cloner = cloner;
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return sharedIndexInformer.getStore().list().stream().filter(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
      return sharedIndexInformer.getStore().list().stream()
          .filter(r -> r.getMetadata().getNamespace().equals(namespace));
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    var resource = sharedIndexInformer.getStore()
        .getByKey(io.fabric8.kubernetes.client.informers.cache.Cache.namespaceKeyFunc(
            resourceID.getNamespace().orElse(null),
            resourceID.getName()));
    if (resource == null) {
      return Optional.empty();
    } else {
      return Optional.of(cloner.clone(resource));
    }
  }

  @Override
  public Stream<ResourceID> keys() {
    return sharedIndexInformer.getStore().list().stream().map(ResourceID::fromResource);

  }
}
