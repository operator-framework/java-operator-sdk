package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.cache.Cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingFilteringEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventFilter;

public class PollingEventSource<T> extends CachingFilteringEventSource<T> {

  private static final Logger log = LoggerFactory.getLogger(PollingEventSource.class);

  private final Timer timer = new Timer();
  private final Supplier<Map<ResourceID, T>> supplierToPoll;
  private final long period;

  public PollingEventSource(Supplier<Map<ResourceID, T>> supplier,
      long period, EventFilter<T> eventFilter, Cache<ResourceID,T> cache) {
    super(cache, eventFilter);
    this.supplierToPoll = supplier;
    this.period = period;
  }

  @Override
  public void start() throws OperatorException {
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (!isRunning()) {
          log.debug("Event source not start yet. Will not run.");
          return;
        }
        getStateAndFillCache();
      }
    }, period, period);
  }

  protected void getStateAndFillCache() {
    var values = supplierToPoll.get();
    values.forEach((k, v) -> super.handleEvent(v, k));
    var keysToRemove = StreamSupport.stream(cache.spliterator(), false)
        .filter(e -> !values.containsKey(e.getKey())).map(Cache.Entry::getKey)
        .collect(Collectors.toList());
    keysToRemove.forEach(super::handleDelete);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }

  public Optional<T> getResourceFromCacheOrSupplier(ResourceID resourceID) {
    var resource = getCachedValue(resourceID);
    if (resource.isPresent()) {
      return resource;
    }
    getStateAndFillCache();
    return getCachedValue(resourceID);
  }
}
