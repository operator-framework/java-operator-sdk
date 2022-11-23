package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;

public class ObjectTransformingItemStore<R extends HasMetadata> implements ItemStore<R> {

  private Function<R, String> keyFunction;
  private Function<R, R> transformationFunction;
  private ConcurrentHashMap<String, R> store = new ConcurrentHashMap<>();

  public ObjectTransformingItemStore(Function<R, R> transformationFunction) {
    this(Cache::metaNamespaceKeyFunc, transformationFunction);
  }

  public ObjectTransformingItemStore(Function<R, String> keyFunction,
      Function<R, R> transformationFunction) {
    this.keyFunction = keyFunction;
    this.transformationFunction = transformationFunction;
  }

  @Override
  public String getKey(R obj) {
    return keyFunction.apply(obj);
  }

  @Override
  public R put(String key, R obj) {
    var transformed = transformationFunction.apply(obj);
    // resource must be always stored.
    transformed.getMetadata().setResourceVersion(obj.getMetadata().getResourceVersion());
    return store.put(key, transformed);
  }

  @Override
  public R remove(String key) {
    return store.remove(key);
  }

  @Override
  public Stream<String> keySet() {
    return store.keySet().stream();
  }

  @Override
  public Stream<R> values() {
    return store.values().stream();
  }

  @Override
  public R get(String key) {
    return store.get(key);
  }

  @Override
  public int size() {
    return store.size();
  }

  @Override
  public boolean isFullState() {
    return false;
  }
}
