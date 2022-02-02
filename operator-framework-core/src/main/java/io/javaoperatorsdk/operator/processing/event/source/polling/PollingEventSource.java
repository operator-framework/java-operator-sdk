package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;

public class PollingEventSource<T, P extends HasMetadata> extends CachingEventSource<T, P> {

  private static final Logger log = LoggerFactory.getLogger(PollingEventSource.class);

  private final Timer timer = new Timer();
  private final Supplier<Map<ResourceID, T>> supplierToPoll;
  private final long period;
  private final long initialDelay;

  /**
   * The initial delay can be configured, however the default is set to period since on startup
   * operator will reconcile so the first should happen naturally on period.
   * 
   * @param supplier poll the target API
   * @param period of polling
   * @param resourceClass type of resource polled
   */
  public PollingEventSource(Supplier<Map<ResourceID, T>> supplier,
      long period, Class<T> resourceClass) {
    this(supplier, period, period, resourceClass);
  }

  public PollingEventSource(Supplier<Map<ResourceID, T>> supplier, long initialDelay,
      long period, Class<T> resourceClass) {
    super(resourceClass);
    this.supplierToPoll = supplier;
    this.period = period;
    this.initialDelay = initialDelay;
  }

  @Override
  public void start() throws OperatorException {
    super.start();
    getStateAndFillCache();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (!isRunning()) {
          log.debug("Event source not yet started. Will not run.");
          return;
        }
        getStateAndFillCache();
      }
    }, initialDelay, period);
  }

  protected void getStateAndFillCache() {
    var values = supplierToPoll.get();
    values.forEach((k, v) -> super.handleEvent(v, k));
    cache.keys().filter(e -> !values.containsKey(e)).forEach(super::handleDelete);
  }

  public void put(ResourceID key, T resource) {
    cache.put(key, resource);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }

  /**
   * See {@link PerResourcePollingEventSource} for more info.
   * 
   * @param primary custom resource
   * @return related resource
   */
  @Override
  public Optional<T> getAssociated(P primary) {
    return getCachedValue(ResourceID.fromResource(primary));
  }
  
}
