package io.javaoperatorsdk.operator.processing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.Metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;

@SuppressWarnings("rawtypes")
public class CustomResourceCache<T extends CustomResource<?, ?>> {

  private static final Logger log = LoggerFactory.getLogger(CustomResourceCache.class);
  private static final Predicate passthrough = o -> true;

  private final ObjectMapper objectMapper;
  private final ConcurrentMap<String, T> resources;
  private final Lock lock = new ReentrantLock();

  public CustomResourceCache() {
    this(new ObjectMapper(), Metrics.NOOP);
  }

  public CustomResourceCache(ObjectMapper objectMapper, Metrics metrics) {
    this.objectMapper = objectMapper;
    if (metrics == null) {
      metrics = Metrics.NOOP;
    }
    resources = metrics.monitorSizeOf(new ConcurrentHashMap<>(), "cache");
  }

  @SuppressWarnings("unchecked")
  public void cacheResource(T resource) {
    cacheResource(resource, passthrough);
  }

  public void cacheResource(T resource, Predicate<T> predicate) {
    try {
      lock.lock();
      final var uid = getUID(resource);
      if (predicate.test(resources.get(uid))) {
        if (passthrough != predicate) {
          log.trace("Update cache after condition is true: {}", getName(resource));
        }
        // defensive copy
        resources.put(getUID(resource), clone(resource));
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * We clone the object so the one in the cache is not changed by the controller or dispatcher.
   * Therefore the cached object always represents the object coming from the API server.
   *
   * @param uuid
   * @return
   */
  public Optional<T> getLatestResource(String uuid) {
    return Optional.ofNullable(resources.get(uuid)).map(this::clone);
  }

  public List<T> getLatestResources(Predicate<CustomResource> selector) {
    try {
      lock.lock();
      return resources.values().stream()
          .filter(selector)
          .map(this::clone)
          .collect(Collectors.toList());
    } finally {
      lock.unlock();
    }
  }

  public Set<String> getLatestResourcesUids(Predicate<CustomResource> selector) {
    try {
      lock.lock();
      return resources.values().stream()
          .filter(selector)
          .map(r -> r.getMetadata().getUid())
          .collect(Collectors.toSet());
    } finally {
      lock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  private T clone(CustomResource customResource) {
    try {
      return (T) objectMapper.readValue(
          objectMapper.writeValueAsString(customResource), customResource.getClass());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public T cleanup(String customResourceUid) {
    return resources.remove(customResourceUid);
  }
}
