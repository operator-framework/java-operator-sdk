package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventFilter;
import io.javaoperatorsdk.operator.processing.event.source.LifecycleAwareEventSource;

public class PollingEventSource<T> extends LifecycleAwareEventSource {

  private static final Logger log = LoggerFactory.getLogger(PollingEventSource.class);

  private final Timer timer = new Timer();
  private Supplier<Map<ResourceID, T>> supplierToPoll;
  private CacheManager cacheManager;
  private Cache<ResourceID, T> cache;
  private EventFilter<T> eventFilter;
  private long period;

  public PollingEventSource(Supplier<Map<ResourceID, T>> supplier,
      CachingProvider cachingProvider,
      long period, EventFilter<T> eventFilter) {
    this.supplierToPoll = supplier;
    this.eventFilter = eventFilter;
    cacheManager = cachingProvider.getCacheManager();
    this.period = period;
    // todo
    MutableConfiguration<ResourceID, T> config = new MutableConfiguration<>();
    cache = cacheManager.createCache("pollingCache", config);
  }

  @Override
  public void start() throws OperatorException {
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (!isStarted()) {
          log.debug("Event source not start yet. Will not run.");
          return;
        }
        getStateAndFillCache();
      }
    }, period, period);
  }

  private void getStateAndFillCache() {
    var values = supplierToPoll.get();
    values.forEach((k, v) -> {
      var cachedValue = cache.get(k);
      if (cachedValue == null || !cachedValue.equals(v)) {
        cache.put(k, v);
        if (eventFilter == null || eventFilter.accept(v, cachedValue, k)) {
          eventHandler.handleEvent(new Event(k));
        }
      }
    });
    var keysToRemove = StreamSupport.stream(cache.spliterator(), false)
        .filter(e -> !values.containsKey(e.getKey())).map(Cache.Entry::getKey)
        .collect(Collectors.toList());
    keysToRemove.forEach(k -> {
      cache.remove(k);
      eventHandler.handleEvent(new Event(k));
    });
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
    cacheManager.close();
    // todo check cache vs cache manager
  }

  public Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  public Optional<T> getResourceFromCacheOrSupplier(ResourceID resourceID) {
    var resource = getResourceFromCache(resourceID);
    if (resource.isPresent()) {
      return resource;
    }
    getStateAndFillCache();
    return getResourceFromCache(resourceID);
  }
}
