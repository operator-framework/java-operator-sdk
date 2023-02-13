package io.javaoperatorsdk.operator.processing.event.source.cache;

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

public class BoundedItemStore<R extends HasMetadata>
    implements ItemStore<R> {

  private Logger log = LoggerFactory.getLogger(BoundedItemStore.class);

  private final ResourceFetcher<String, R> resourceFetcher;
  private final BoundedCache<String, R> cache;
  private final Function<R, String> keyFunction;
  private final Map<String, R> existingMinimalResources = new ConcurrentHashMap<>();
  private final Class<R> resourceClass;

  public BoundedItemStore(KubernetesClient client,
      BoundedCache<String, R> cache, Class<R> resourceClass) {
    this(client, cache, resourceClass, namespaceKeyFunc());
  }

  public BoundedItemStore(KubernetesClient client,
      BoundedCache<String, R> cache,
      Class<R> resourceClass,
      Function<R, String> keyFunction) {
    this.resourceFetcher = new KubernetesResourceFetcher<>(resourceClass, client);
    this.cache = cache;
    this.keyFunction = keyFunction;
    this.resourceClass = resourceClass;
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
      R minimal = resourceClass.getConstructor().newInstance();
      minimal.setMetadata(new ObjectMetaBuilder().build());
      minimal.getMetadata().setName(obj.getMetadata().getName());
      minimal.getMetadata().setNamespace(obj.getMetadata().getNamespace());
      minimal.getMetadata().setResourceVersion(obj.getMetadata().getResourceVersion());
      return minimal;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
        | NoSuchMethodException e) {
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
    log.debug("Fetching resource from server");
    var newRes = resourceFetcher.fetchResource(key);
    synchronized (this) {
      log.debug("Fetched resource: {}", newRes);
      if (newRes == null) {
        existingMinimalResources.remove(key);
        return null;
      }
      // Just want to put the fetched resource if there is still no resource published from
      // different source. In case of informers actually multiple events might arrive, therefore non
      // fetched resource should take always precedence.
      var actual = cache.get(key);
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
