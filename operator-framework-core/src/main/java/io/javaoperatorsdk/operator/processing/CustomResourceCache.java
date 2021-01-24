package io.javaoperatorsdk.operator.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.CustomResource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomResourceCache {

  private static final Logger log = LoggerFactory.getLogger(CustomResourceCache.class);

  private ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, CustomResource> resources = new ConcurrentHashMap<>();
  private final Lock lock = new ReentrantLock();

  public void cacheResource(CustomResource resource) {
    try {
      lock.lock();
      resources.put(KubernetesResourceUtils.getUID(resource), resource);
    } finally {
      lock.unlock();
    }
  }

  public void cacheResource(CustomResource resource, Predicate<CustomResource> predicate) {
    try {
      lock.lock();
      if (predicate.test(resources.get(KubernetesResourceUtils.getUID(resource)))) {
        log.trace("Update cache after condition is true: {}", resource);
        resources.put(resource.getMetadata().getUid(), resource);
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
  public Optional<CustomResource> getLatestResource(String uuid) {
    return Optional.ofNullable(clone(resources.get(uuid)));
  }

  private CustomResource clone(CustomResource customResource) {
    try {
      if (customResource == null) {
        return null;
      }
      CustomResource clonedObject =
          objectMapper.readValue(
              objectMapper.writeValueAsString(customResource), customResource.getClass());
      return clonedObject;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public CustomResource cleanup(String customResourceUid) {
    return resources.remove(customResourceUid);
  }
}
