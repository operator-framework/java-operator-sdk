package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;

public class ExternalResourceCachingEventSource<R, P extends HasMetadata>
    extends CachingEventSource<R, P> implements RecentOperationCacheFiller<R> {

  public ExternalResourceCachingEventSource(Class<R> resourceClass) {
    super(resourceClass);
  }

  public synchronized void handleDelete(ObjectKey relatedObjectKey) {
    if (!isRunning()) {
      return;
    }
    var cachedValue = cache.get(relatedObjectKey);
    cache.remove(relatedObjectKey);
    // we only propagate event if the resource was previously in cache
    if (cachedValue.isPresent()) {
      getEventHandler().handleEvent(new Event(relatedObjectKey));
    }
  }

  public synchronized void handleEvent(R value, ObjectKey relatedObjectKey) {
    if (!isRunning()) {
      return;
    }
    var cachedValue = cache.get(relatedObjectKey);
    if (cachedValue.map(v -> !v.equals(value)).orElse(true)) {
      cache.put(relatedObjectKey, value);
      getEventHandler().handleEvent(new Event(relatedObjectKey));
    }
  }

  @Override
  public synchronized void handleRecentResourceCreate(ObjectKey objectKey, R resource) {
    if (cache.get(objectKey).isEmpty()) {
      cache.put(objectKey, resource);
    }
  }

  @Override
  public synchronized void handleRecentResourceUpdate(ObjectKey objectKey, R resource,
      R previousResourceVersion) {
    cache.get(objectKey).ifPresent(r -> {
      if (r.equals(previousResourceVersion)) {
        cache.put(objectKey, resource);
      }
    });
  }
}
