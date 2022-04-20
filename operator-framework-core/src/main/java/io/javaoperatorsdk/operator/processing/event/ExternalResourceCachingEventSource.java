package io.javaoperatorsdk.operator.processing.event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.source.*;

public abstract class ExternalResourceCachingEventSource<R, P extends HasMetadata>
    extends AbstractResourceEventSource<R, P> implements RecentOperationCacheFiller<R> {

  protected final IDMapper<R> idProvider;
  
  protected Map<ResourceID, Map<String,R>> cache = new ConcurrentHashMap<>();

  protected ExternalResourceCachingEventSource(Class<R> resourceClass, IDMapper<R> idProvider) {
    super(resourceClass);
    this.idProvider = idProvider;
  }

  public synchronized void handleDelete(ResourceID primaryID, String resourceID) {
    if (!isRunning()) {
      return;
    }
    var cachedValues = cache.get(primaryID);
    R cachedResource = cachedValues.remove(resourceID);

    if (cachedValues.isEmpty()) {
      cache.remove(primaryID);
    }
    // we only propagate event if the resource was previously in cache
    if (cachedResource != null) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  public synchronized void handleEvent(R resource, ResourceID primaryID) {
    if (!isRunning()) {
      return;
    }
    var resourceId = idProvider.apply(resource);
    var cachedValues = cache.get(primaryID);
    if (cachedValues == null) {
      Map<String,R> values = new HashMap<>();
      values.put(resourceId,resource);
      cache.put(primaryID,values);
    } else {
      var actualResource = cachedValues.get(resourceId);
      cachedValues.put(resourceId,resource);
      if (actualResource != null) {
        if (!actualResource.equals(resource)) {
          getEventHandler().handleEvent(new Event(primaryID));
        }
      }
    }
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID primaryID, R resource) {
    var actualValues = cache.get(primaryID);
    var resourceId = idProvider.apply(resource);
    if (actualValues == null) {
      actualValues = new HashMap<>();
      cache.put(primaryID,actualValues);
      actualValues.put(resourceId,resource);
    } else {
      actualValues.computeIfAbsent(resourceId,r -> resource);
    }
  }

  @Override
  public synchronized void handleRecentResourceUpdate(ResourceID primaryID, R resource,
      R previousVersionOfResource) {
    var actualValues= cache.get(primaryID);
    if (actualValues != null) {
      var resourceId = idProvider.apply(resource);
      R actualResource = actualValues.get(resourceId);
      if (actualResource.equals(previousVersionOfResource)) {
        actualValues.put(resourceId,resource);
      }
    }
  }

  @Override
  public Set<R> getSecondaryResources(P primary) {
    return new HashSet<>(cache.get(ResourceID.fromResource(primary)).values());
  }

  protected UpdatableCache<R> initCache() {
    return new ConcurrentHashMapCache<>();
  }
}
