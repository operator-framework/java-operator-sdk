package io.javaoperatorsdk.operator.processing.event.source.inbound;

import javax.cache.Cache;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingFilteringEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventFilter;

public class CachingInboundEventSource<T> extends CachingFilteringEventSource<T> {

  public CachingInboundEventSource(Cache<ResourceID, T> cache) {
    this(cache, null);
  }

  public CachingInboundEventSource(Cache<ResourceID, T> cache, EventFilter<T> eventFilter) {
    super(cache, eventFilter);
  }

  public void handleResourceEvent(T resource, ResourceID relatedResourceID) {
    super.handleEvent(resource, relatedResourceID);
  }

  public void handleResourceDeleteEvent(ResourceID resourceID) {
    super.handleDelete(resourceID);
  }
}
