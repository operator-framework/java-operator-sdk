package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

public class InformerResourceCache<T extends HasMetadata> implements ResourceCache<T> {

  private final SharedInformer<T> sharedInformer;

  public InformerResourceCache(SharedInformer<T> sharedInformer) {
    this.sharedInformer = sharedInformer;
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return Optional.ofNullable(sharedInformer.getStore()
        .getByKey(Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null),
            resourceID.getName())));
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return sharedInformer.getStore().list().stream().filter(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    return sharedInformer.getStore().list().stream()
        .filter(v -> namespace.equals(v.getMetadata().getNamespace()) && predicate.test(v));
  }
}
