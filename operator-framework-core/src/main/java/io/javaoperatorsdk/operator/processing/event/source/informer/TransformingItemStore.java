package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;

public class TransformingItemStore<R extends HasMetadata> implements ItemStore<R> {

  private final Function<R, String> keyFunction;
  private final UnaryOperator<R> transformationFunction;
  private final ConcurrentHashMap<String, R> store = new ConcurrentHashMap<>();

  public TransformingItemStore(UnaryOperator<R> transformationFunction) {
    this(Cache::metaNamespaceKeyFunc, transformationFunction);
  }

  public TransformingItemStore(
      Function<R, String> keyFunction, UnaryOperator<R> transformationFunction) {
    this.keyFunction = keyFunction;
    this.transformationFunction = transformationFunction;
  }

  @Override
  public String getKey(R obj) {
    return keyFunction.apply(obj);
  }

  @Override
  public R put(String key, R obj) {
    var originalName = obj.getMetadata().getName();
    var originalNamespace = obj.getMetadata().getNamespace();
    var originalResourceVersion = obj.getMetadata().getResourceVersion();

    var transformed = transformationFunction.apply(obj);

    transformed.getMetadata().setName(originalName);
    transformed.getMetadata().setNamespace(originalNamespace);
    transformed.getMetadata().setResourceVersion(originalResourceVersion);
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
