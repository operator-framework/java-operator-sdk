package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;

public class PollingEventSource<T> extends CachingEventSource<T> {

  private static final Logger log = LoggerFactory.getLogger(PollingEventSource.class);

  private final Timer timer = new Timer();
  private final Supplier<Map<ResourceID, T>> supplierToPoll;
  private final long period;

  public PollingEventSource(Supplier<Map<ResourceID, T>> supplier,
      long period) {
    this.supplierToPoll = supplier;
    this.period = period;
  }

  @Override
  public void start() throws OperatorException {
    super.start();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (!isRunning()) {
          log.debug("Event source not yet started. Will not run.");
          return;
        }
        getStateAndFillCache();
      }
    }, period, period);
  }

  protected void getStateAndFillCache() {
    var values = supplierToPoll.get();
    values.forEach((k, v) -> super.handleEvent(v, k));
    cache.keySet().stream()
        .filter(e -> !values.containsKey(e))
        .forEach(super::handleDelete);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }

  public Optional<T> getValueFromCacheOrSupplier(ResourceID resourceID) {
    var resource = getCachedValue(resourceID);
    if (resource.isPresent()) {
      return resource;
    }
    getStateAndFillCache();
    return getCachedValue(resourceID);
  }
}
