package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

public abstract class ManagedInformerEventSource<R extends HasMetadata, P extends HasMetadata, C extends ResourceConfiguration<R>>
    extends CachingEventSource<R, P>
    implements ResourceEventHandler<R>, ResourceCache<R> {

  protected TemporalResourceCache<R> temporalResourceCache = new TemporalResourceCache<>(this);

  protected ManagedInformerEventSource(
      MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client, C configuration) {
    super(configuration.getResourceClass());
    manager().initSources(client, configuration, this);
  }

  @Override
  public void onAdd(R resource) {
    temporalResourceCache.onAdd(resource);
  }

  @Override
  public void onUpdate(R oldObj, R newObj) {
    temporalResourceCache.onUpdate(oldObj, newObj);
  }

  @Override
  public void onDelete(R obj, boolean deletedFinalStateUnknown) {
    temporalResourceCache.onDelete(obj, deletedFinalStateUnknown);
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

  public void handleJustUpdatedResource(R resource, String previousResourceVersion) {
    temporalResourceCache.putUpdatedResource(resource, previousResourceVersion);
  }

  public void handleJustAddedResource(R resource) {
    temporalResourceCache.putAddedResource(resource);
  }

  @Override
  public Optional<R> get(ResourceID resourceID) {
    Optional<R> resource = temporalResourceCache.getResourceFromCache(resourceID);
    if (resource.isPresent()) {
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
    var res = temporalResourceCache.getResourceFromCache(ResourceID.fromResource(resource));
    if (res.isEmpty()) {
      return false;
    } else {
      return res.get().getMetadata().getResourceVersion()
          .equals(resource.getMetadata().getResourceVersion());
    }
  }

  @Override
  public Stream<R> list(String namespace, Predicate<R> predicate) {
    return manager().list(namespace, predicate);
  }
}
