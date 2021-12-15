package io.javaoperatorsdk.operator.processing.event.source.inbound;

import javax.cache.Cache;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;

public class CachingInboundEventSource<T, P extends HasMetadata> extends CachingEventSource<T, P> {

  public CachingInboundEventSource(Cache<ResourceID, T> cache, Class<P> resourceClass) {
    super(cache, resourceClass);
  }

  public void handleResourceEvent(T resource, ResourceID relatedResourceID) {
    super.handleEvent(resource, relatedResourceID);
  }

  public void handleResourceDeleteEvent(ResourceID resourceID) {
    super.handleDelete(resourceID);
  }
}
