package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Supplier;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class PollingEventSource<T> extends AbstractEventSource {

  private final Timer timer = new Timer();
  private Supplier<Map<ResourceID, T>> supplierToPoll;
  private CacheManager cacheManager;
  private Cache<ResourceID, T> cache;
  private long period;

  public PollingEventSource(Supplier<Map<ResourceID, T>> supplier,
      CachingProvider cachingProvider,
      long period) {
    this.supplierToPoll = supplier;
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
        getStateAndFillCache();
      }
    }, period, period);
  }

  private void getStateAndFillCache() {
    var values = supplierToPoll.get();
    values.forEach((k, v) -> cache.put(k, v));
  }

  @Override
  public void stop() throws OperatorException {
    timer.cancel();
    cacheManager.close();
  }

  public Optional<T> getState(ResourceID customResourceID) {
    return Optional.ofNullable(cache.get(customResourceID));
  }

  public Optional<T> getStateFromSupplier(ResourceID customResourceID) {
    getStateAndFillCache();
    return getState(customResourceID);
  }

}
