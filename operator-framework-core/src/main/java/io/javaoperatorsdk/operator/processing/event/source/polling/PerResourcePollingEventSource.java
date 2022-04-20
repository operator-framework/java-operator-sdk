package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.processing.event.source.IDMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

/**
 *
 * Polls the supplier for each controlled resource registered. Resource is registered when created
 * if there is no registerPredicate provided. If register predicate provided it is evaluated on
 * resource create and/or update to register polling for the event source.
 * <p>
 * For other behavior see {@link CachingEventSource}
 *
 * @param <R> the resource polled by the event source
 * @param <P> related custom resource
 */
public class PerResourcePollingEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P>
    implements ResourceEventAware<P> {

  private static final Logger log = LoggerFactory.getLogger(PerResourcePollingEventSource.class);

  private final Timer timer = new Timer();
  private final Map<ResourceID, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceFetcher<R, P> resourceFetcher;
  private final Cache<P> resourceCache;
  private final Predicate<P> registerPredicate;
  private final long period;

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
                                       Cache<P> resourceCache, long period, Class<R> resourceClass, IDMapper<R> idProvider) {
    this(resourceFetcher, resourceCache, period, null, resourceClass, idProvider);
  }

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
                                       Cache<P> resourceCache, long period,
                                       Predicate<P> registerPredicate, Class<R> resourceClass,
                                       IDMapper<R> idProvider) {
    super(resourceClass, idProvider);
    this.resourceFetcher = resourceFetcher;
    this.resourceCache = resourceCache;
    this.period = period;
    this.registerPredicate = registerPredicate;
  }

  private void pollForResource(P primary) {
    var primaryID = ResourceID.fromResource(primary);
    var value = resourceFetcher.fetchResources(primary);
    var newResourceIDs = value.stream().map(idProvider::apply).collect(Collectors.toSet());
    var cachedValues = cache.get(primaryID);

    Set<String> toDelete = cachedValues.keySet().stream().filter(r -> !newResourceIDs.contains(r))
            .collect(Collectors.toSet());

    toDelete.forEach(resourceID -> handleDelete(primaryID,resourceID));
    value.forEach(v->super.handleEvent(v,primaryID));
  }

  private Set<R> getAndCacheResource(ResourceID resourceID) {
    var resource = resourceCache.get(resourceID);
    if (resource.isPresent()) {
      var values = resourceFetcher.fetchResources(resource.get());
      values.forEach(r-> handleEvent(r,resourceID));
      return values;
    } else {
      return Collections.emptySet();
    }
  }

  @Override
  public void onResourceCreated(P resource) {
    checkAndRegisterTask(resource);
  }

  @Override
  public void onResourceUpdated(P newResource, P oldResource) {
    checkAndRegisterTask(newResource);
  }

  @Override
  public void onResourceDeleted(P resource) {
    var resourceID = ResourceID.fromResource(resource);
    TimerTask task = timerTasks.remove(resourceID);
    if (task != null) {
      log.debug("Canceling task for resource: {}", resource);
      task.cancel();
    }
    cache.remove(resourceID);
  }

  // This method is always called from the same Thread for the same resource,
  // since events from ResourceEventAware are propagated from the thread of the informer. This is
  // important
  // because otherwise there will be a race condition related to the timerTasks.
  private void checkAndRegisterTask(P resource) {
    var resourceID = ResourceID.fromResource(resource);
    if (timerTasks.get(resourceID) == null && (registerPredicate == null
        || registerPredicate.test(resource))) {
      var task =
          new TimerTask() {
            @Override
            public void run() {
              if (!isRunning()) {
                log.debug("Event source not yet started. Will not run for: {}", resourceID);
                return;
              }
              // always use up-to-date resource from cache
              var res = resourceCache.get(resourceID);
              res.ifPresentOrElse(
                  PerResourcePollingEventSource.this::pollForResource,
                  () -> log.warn("No resource in cache for resource ID: {}", resourceID));
            }
          };
      timerTasks.put(resourceID, task);
      timer.schedule(task, 0, period);
    }
  }

  /**
   * When this event source is queried for the resource, it might not be fully "synced". Thus, the
   * cache might not be propagated, therefore the supplier is checked for the resource too.
   *
   * @param primary resource of the controller
   * @return the related resource for this event source
   */
  @Override
  public Set<R> getSecondaryResources(P primary) {
    return getValueFromCacheOrSupplier(ResourceID.fromResource(primary));
  }

  /**
   *
   * @param resourceID of the target related resource
   * @return the cached value of the resource, if not present it gets the resource from the
   *         supplier. The value provided from the supplier is cached, but no new event is
   *         propagated.
   */
  public Set<R> getValueFromCacheOrSupplier(ResourceID resourceID) {
    var cachedValue = cache.get(resourceID);
    if (cachedValue != null) {
      return new HashSet<>(cachedValue.values());
    } else {
      return getAndCacheResource(resourceID);
    }
  }

  public interface ResourceFetcher<R, P> {
    Set<R> fetchResources(P primaryResource);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }
}
