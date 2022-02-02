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

  public static final long DEFAULT_INITIAL_DELAY = 1000;

  private final Timer timer = new Timer();
  private final Supplier<Map<ResourceID, T>> supplierToPoll;
  private final long period;
  private final long initialDelay;

  public PollingEventSource(Supplier<Map<ResourceID, T>> supplier,
                            long period, Class<T> resourceClass) {
    this(supplier,DEFAULT_INITIAL_DELAY,period,resourceClass);
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
    return getValueFromCacheOrSupplier(ResourceID.fromResource(primary));
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
