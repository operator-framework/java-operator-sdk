package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;

public class BoundedItemStore<R extends HasMetadata> extends BoundedStore<String, R>
    implements ItemStore<R> {

  private final Function<R, String> keyFunction;

  public BoundedItemStore(KubernetesClient client, Class<R> resourceClass,
      BoundedCache<String, R> cache) {
    this(new KubernetesResourceFetcher<>(resourceClass, client), cache, namespaceKeyFunc());
  }

  public BoundedItemStore(KubernetesResourceFetcher<R> resourceFetcher,
      BoundedCache<String, R> cache, Function<R, String> keyFunction) {
    super(resourceFetcher, cache);
    this.keyFunction = keyFunction;
  }

  @Override
  public String getKey(R obj) {
    return keyFunction.apply(obj);
  }

  @Override
  public Stream<String> keySet() {
    return super.keys();
  }

  /**
   * This is very inefficient but should not be called by the Informer or just before it's started
   */
  @Override
  public Stream<R> values() {
    var keys = cache.keys();
    var values = cache.values();
    var notPresentValueKeys = new HashSet<>(existingResources);
    notPresentValueKeys.retainAll(keys);
    var fetchedValues = notPresentValueKeys.stream().map(k -> fetchAndCacheResource(k));
    return Stream.concat(values.stream(), fetchedValues);
  }

  @Override
  public int size() {
    return existingResources.size();
  }

  public static <R extends HasMetadata> Function<R, String> namespaceKeyFunc() {
    return r -> Cache.namespaceKeyFunc(r.getMetadata().getNamespace(), r.getMetadata().getName());
  }
}
