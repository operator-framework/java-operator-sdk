package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.Utils;

public class BoundedItemStore<R extends HasMetadata> implements ItemStore<R> {

  private static final Logger log = LoggerFactory.getLogger(BoundedItemStore.class);

  private final ResourceFetcher<String, R> resourceFetcher;
  private final BoundedCache<String, R> cache;
  private final Function<R, String> keyFunction;
  private final Map<String, R> existingMinimalResources = new ConcurrentHashMap<>();
  private final Constructor<R> resourceConstructor;

  public BoundedItemStore(
      BoundedCache<String, R> cache, Class<R> resourceClass, KubernetesClient client) {
    this(
        cache,
        resourceClass,
        namespaceKeyFunc(),
        new KubernetesResourceFetcher<>(resourceClass, client));
  }

  public BoundedItemStore(
      BoundedCache<String, R> cache,
      Class<R> resourceClass,
      Function<R, String> keyFunction,
      ResourceFetcher<String, R> resourceFetcher) {
    this.resourceFetcher = resourceFetcher;
    this.cache = cache;
    this.keyFunction = keyFunction;
    this.resourceConstructor = Utils.getConstructor(resourceClass);
  }

  @Override
  public String getKey(R obj) {
    return keyFunction.apply(obj);
  }

  @Override
  public synchronized R put(String key, R obj) {
    var result = existingMinimalResources.get(key);
    cache.put(key, obj);
    existingMinimalResources.put(key, createMinimalResource(obj));
    return result;
  }

  private R createMinimalResource(R obj) {
    try {
      R minimal = resourceConstructor.newInstance();
      final var metadata = obj.getMetadata();
      minimal.setMetadata(
          new ObjectMetaBuilder()
              .withName(metadata.getName())
              .withNamespace(metadata.getNamespace())
              .withResourceVersion(metadata.getResourceVersion())
              .build());
      return minimal;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public synchronized R remove(String key) {
    var fullValue = cache.remove(key);
    var minimalValue = existingMinimalResources.remove(key);
    return fullValue != null ? fullValue : minimalValue;
  }

  @Override
  public Stream<String> keySet() {
    return existingMinimalResources.keySet().stream();
  }

  @Override
  public Stream<R> values() {
    return existingMinimalResources.values().stream();
  }

  @Override
  public int size() {
    return existingMinimalResources.size();
  }

  @Override
  public R get(String key) {
    var res = cache.get(key);
    if (res != null) {
      return res;
    }
    if (!existingMinimalResources.containsKey(key)) {
      return null;
    } else {
      return refreshMissingStateFromServer(key);
    }
  }

  @Override
  public boolean isFullState() {
    return false;
  }

  public static <R extends HasMetadata> Function<R, String> namespaceKeyFunc() {
    return r -> Cache.namespaceKeyFunc(r.getMetadata().getNamespace(), r.getMetadata().getName());
  }

  protected R refreshMissingStateFromServer(String key) {
    log.debug("Fetching resource from server for key: {}", key);
    var newRes = resourceFetcher.fetchResource(key);
    synchronized (this) {
      log.debug("Fetched resource: {}", newRes);
      var actual = cache.get(key);
      if (newRes == null) {
        // double-checking if actual, not received since.
        // If received we just return. Since the resource from informer should be always leading,
        // even if the fetched resource is null, this will be eventually received as an event.
        if (actual == null) {
          existingMinimalResources.remove(key);
          return null;
        } else {
          return actual;
        }
      }
      // Just want to put the fetched resource if there is still no resource published from
      // different source. In case of informers actually multiple events might arrive, therefore non
      // fetched resource should take always precedence.
      if (actual == null) {
        cache.put(key, newRes);
        existingMinimalResources.put(key, createMinimalResource(newRes));
        return newRes;
      } else {
        return actual;
      }
    }
  }
}
