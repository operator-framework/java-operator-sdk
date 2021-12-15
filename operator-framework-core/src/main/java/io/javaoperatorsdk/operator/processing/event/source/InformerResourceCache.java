package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

public class InformerResourceCache<T extends HasMetadata> implements ResourceCache<T> {

  private final SharedInformer<T> informer;
  private final Cloner cloner;

  public InformerResourceCache(SharedInformer<T> informer, Cloner cloner) {
    this.informer = informer;
    this.cloner = cloner;
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    final var resource = informer.getStore().getByKey(
        Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null), resourceID.getName()));
    return Optional.ofNullable(cloner.clone(resource));
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return informer.getStore().list().stream().filter(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    final var stream = informer.getStore().list().stream()
        .filter(r -> namespace.equals(r.getMetadata().getNamespace()));
    return predicate != null ? stream.filter(predicate) : stream;
  }
}
