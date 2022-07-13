package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;

/**
 * Polls resource (on contrary to {@link PerResourcePollingEventSource}) not per resource bases but
 * instead to calls supplier periodically and independently of the number or state of custom
 * resources managed by the controller. It is called on start (synced). This means that when the
 * reconciler first time executed on startup the first poll already happened before. So if the cache
 * does not contain the target resource it means it is not created yet or was deleted while an
 * operator was not running.
 *
 * <p>
 * Another caveat with this is if the cached object is checked in the reconciler and created since
 * not in the cache it should be manually added to the cache, since it can happen that the
 * reconciler is triggered before the cache is propagated with the new resource from a scheduled
 * execution. See {@link #handleRecentResourceCreate(ResourceID, Object)} and update method. So the
 * generic workflow in reconciler should be:
 *
 * <ul>
 * <li>Check if the cache contains the resource.
 * <li>If cache contains the resource reconcile it - compare with target state, update if necessary
 * <li>if cache not contains the resource create it.
 * <li>If the resource was created or updated, put the new version of the resource manually to the
 * cache.
 * </ul>
 *
 * @param <R> type of the polled resource
 * @param <P> primary resource type
 */
public class PollingEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P> {

  private static final Logger log = LoggerFactory.getLogger(PollingEventSource.class);

  private final Timer timer = new Timer();
  private final GenericResourceFetcher<R> genericResourceFetcher;
  private final long period;

  public PollingEventSource(
      GenericResourceFetcher<R> supplier,
      long period,
      Class<R> resourceClass) {
    super(resourceClass, CacheKeyMapper.singleResourceCacheKeyMapper());
    this.genericResourceFetcher = supplier;
    this.period = period;
  }

  public PollingEventSource(
      GenericResourceFetcher<R> supplier,
      long period,
      Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    super(resourceClass, cacheKeyMapper);
    this.genericResourceFetcher = supplier;
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

  protected synchronized void getStateAndFillCache() {
    var values = genericResourceFetcher.fetchResources();
    handleResources(values);
  }


  public interface GenericResourceFetcher<R> {
    Map<ResourceID, Set<R>> fetchResources();
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }
}
