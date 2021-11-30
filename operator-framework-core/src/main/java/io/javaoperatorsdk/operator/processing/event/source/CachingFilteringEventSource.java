package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;

import javax.cache.Cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public abstract class CachingFilteringEventSource<T> extends LifecycleAwareEventSource {

  private static final Logger log = LoggerFactory.getLogger(CachingFilteringEventSource.class);

  protected Cache<ResourceID, T> cache;
  protected EventFilter<T> eventFilter;

  public CachingFilteringEventSource(Cache<ResourceID, T> cache, EventFilter<T> eventFilter) {
    this.cache = cache;
    this.eventFilter = eventFilter;
  }

  protected void handleDelete(ResourceID relatedResourceID) {
    if (!isRunning()) {
      log.debug("Received event but event for {} source is not running", relatedResourceID);
      return;
    }
    var cachedValue = cache.get(relatedResourceID);
    cache.remove(relatedResourceID);
    // we only propagate event if the resource was previously in cache
    if (cachedValue != null
        && (eventFilter == null || eventFilter.acceptDelete(cachedValue, relatedResourceID))) {
      eventHandler.handleEvent(new Event(relatedResourceID));
    }
  }

  protected void handleEvent(T value, ResourceID relatedResourceID) {
    if (!isRunning()) {
      log.debug("Received event but event for {} source is not running", relatedResourceID);
      return;
    }
    var cachedValue = cache.get(relatedResourceID);
    if (cachedValue == null || !cachedValue.equals(value)) {
      cache.put(relatedResourceID, value);
      if (eventFilter == null || eventFilter.accept(value, cachedValue, relatedResourceID)) {
        eventHandler.handleEvent(new Event(relatedResourceID));
      }
    }
  }

  public Cache<ResourceID, T> getCache() {
    return cache;
  }

  public Optional<T> getCachedValue(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    cache.close();
  }
}
