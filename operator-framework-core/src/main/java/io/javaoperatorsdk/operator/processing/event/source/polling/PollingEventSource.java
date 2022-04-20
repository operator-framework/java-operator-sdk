package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.processing.event.source.IDProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Polls resource (on contrary to {@link PerResourcePollingEventSource}) not per resource bases but
 * instead to calls supplier periodically and independently of the number of state of custom
 * resources managed by the operator. It is called on start (synced). This means that when the
 * reconciler first time executed on startup a poll already happened before. So if the cache does
 * not contain the target resource it means it is not created yet or was deleted while an operator
 * was not running.
 *
 * <p>Another caveat with this is if the cached object is checked in the reconciler and created
 * since not in the cache it should be manually added to the cache, since it can happen that the
 * reconciler is triggered before the cache is propagated with the new resource from a scheduled
 * execution. See {@link #put(ResourceID, Object)} method. So the generic workflow in reconciler
 * should be:
 *
 * <ul>
 *   <li>Check if the cache contains the resource.
 *   <li>If cache contains the resource reconcile it - compare with target state, update if
 *       necessary
 *   <li>if cache not contains the resource create it.
 *   <li>If the resource was created or updated, put the new version of the resource manually to the
 *       cache.
 * </ul>
 *
 * @param <R> type of the polled resource
 * @param <P> primary resource type
 */
public class PollingEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P> {

  private static final Logger log = LoggerFactory.getLogger(PollingEventSource.class);

  private final Timer timer = new Timer();
  private final Supplier<Map<ResourceID, Set<R>>> supplierToPoll;
  private final long period;

  public PollingEventSource(
      Supplier<Map<ResourceID, Set<R>>> supplier,
      long period,
      Class<R> resourceClass,
      Function<R,String> idProvider) {
    super(resourceClass, idProvider);
    this.supplierToPoll = supplier;
    this.period = period;
  }

  @Override
  public void start() throws OperatorException {
    super.start();
    getStateAndFillCache();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            if (!isRunning()) {
              log.debug("Event source not yet started. Will not run.");
              return;
            }
            getStateAndFillCache();
          }
        },
        period,
        period);
  }

  // todo can be optimized;
  protected synchronized void getStateAndFillCache() {
    var values = supplierToPoll.get();
    HashMap<ResourceID,String> toDelete = new HashMap<>();

    cache.forEach(
        (primaryID, resourcesMap) -> {
          var newIds =
              values.get(primaryID).stream()
                  .map(idProvider::apply)
                  .collect(Collectors.toSet());
          resourcesMap.forEach(
              (actualID, actualResource) -> {
                if (!newIds.contains(actualID)) {
                    toDelete.put(primaryID,actualID);
                }
              });
        });
    toDelete.forEach(super::handleDelete);


    values.forEach(
        (k, v) -> v.forEach(r -> handleEvent(r, k)));
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }
}
