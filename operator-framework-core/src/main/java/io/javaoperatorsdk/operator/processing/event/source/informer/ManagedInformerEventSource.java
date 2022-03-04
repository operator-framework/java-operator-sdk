package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

public abstract class ManagedInformerEventSource<R extends HasMetadata, P extends HasMetadata, C extends ResourceConfiguration<R>>
    extends CachingEventSource<R, P>
    implements ResourceEventHandler<R>, ResourceCache<R>, RecentOperationCacheFiller<R> {

  private static final Logger log = LoggerFactory.getLogger(ManagedInformerEventSource.class);

  protected TemporaryResourceCache<R> temporaryResourceCache = new TemporaryResourceCache<>(this);

  protected ManagedInformerEventSource(
      MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client, C configuration) {
    super(configuration.getResourceClass());
    manager().initSources(client, configuration, this);
  }

  @Override
  public void onAdd(R resource) {
    temporaryResourceCache.removeResourceFromCache(resource);
  }

  @Override
  public void onUpdate(R oldObj, R newObj) {
    temporaryResourceCache.removeResourceFromCache(newObj);
  }

  @Override
  public void onDelete(R obj, boolean deletedFinalStateUnknown) {
    temporaryResourceCache.removeResourceFromCache(obj);
  }

  @Override
  protected UpdatableCache<R> initCache() {
    return new InformerManager<>();
  }

  protected InformerManager<R, C> manager() {
    return (InformerManager<R, C>) cache;
  }

  @Override
  public void start() {
    manager().start();
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    manager().stop();
  }

  @Override
  public void handleRecentResourceUpdate(ResourceID resourceID, R resource,
      R previousResourceVersion) {
    temporaryResourceCache.putUpdatedResource(resource,
        previousResourceVersion.getMetadata().getResourceVersion());
  }

  @Override
  public void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    temporaryResourceCache.putAddedResource(resource);
  }

  @Override
  public Optional<R> get(ResourceID resourceID) {
    Optional<R> resource = temporaryResourceCache.getResourceFromCache(resourceID);
    if (resource.isPresent()) {
      log.debug("Resource found in temporal cache for Resource ID: {}", resourceID);
      return resource;
    } else {
      return super.get(resourceID);
    }
  }

  @Override
  public Optional<R> getAssociated(P primary) {
    return get(ResourceID.fromResource(primary));
  }

  @Override
  public Optional<R> getCachedValue(ResourceID resourceID) {
    return get(resourceID);
  }

  protected boolean temporalCacheHasResourceWithVersionAs(R resource) {
    var resourceID = ResourceID.fromResource(resource);
    var res = temporaryResourceCache.getResourceFromCache(resourceID);
    return res.map(r -> {
      boolean resVersionsEqual = r.getMetadata().getResourceVersion()
          .equals(resource.getMetadata().getResourceVersion());
      log.debug("Resource found in temporal cache for id: {} resource versions equal: {}",
          resourceID, resVersionsEqual);
      return resVersionsEqual;
    }).orElse(false);
  }

  @Override
  public Stream<R> list(String namespace, Predicate<R> predicate) {
    return manager().list(namespace, predicate);
  }

  ManagedInformerEventSource<R, P, C> setTemporalResourceCache(
      TemporaryResourceCache<R> temporaryResourceCache) {
    this.temporaryResourceCache = temporaryResourceCache;
    return this;
  }
}
