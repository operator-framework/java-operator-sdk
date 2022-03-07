package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;

public class ExternalResourceCachingEventSource<R, P extends HasMetadata>
    extends CachingEventSource<R, P> implements RecentOperationCacheFiller<R> {

  public ExternalResourceCachingEventSource(Class<R> resourceClass) {
    super(resourceClass);
  }

  public synchronized void handleDelete(ResourceID relatedResourceID) {
    if (!isRunning()) {
      return;
    }
    var cachedValue = cache.get(relatedResourceID);
    cache.remove(relatedResourceID);
    // we only propagate event if the resource was previously in cache
    if (cachedValue.isPresent()) {
      getEventHandler().handleEvent(new Event(relatedResourceID));
    }
  }

  public synchronized void handleEvent(R value, ResourceID relatedResourceID) {
    if (!isRunning()) {
      return;
    }
    var cachedValue = cache.get(relatedResourceID);
    if (cachedValue.map(v -> !v.equals(value)).orElse(true)) {
      cache.put(relatedResourceID, value);
      getEventHandler().handleEvent(new Event(relatedResourceID));
    }
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    if (cache.get(resourceID).isEmpty()) {
      cache.put(resourceID, resource);
    }
  }

  @Override
  public synchronized void handleRecentResourceUpdate(ResourceID resourceID, R resource,
      R previousResourceVersion) {
    cache.get(resourceID).ifPresent(r -> {
      if (r.equals(previousResourceVersion)) {
        cache.put(resourceID, resource);
      }
    });
  }
}
