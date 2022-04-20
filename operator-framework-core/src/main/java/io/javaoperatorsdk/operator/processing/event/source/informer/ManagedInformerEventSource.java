package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

public abstract class ManagedInformerEventSource<R extends HasMetadata, P extends HasMetadata, C extends ResourceConfiguration<R>>
    extends CachingEventSource<R, P>
    implements ResourceEventHandler<R>, IndexerResourceCache<R>, RecentOperationCacheFiller<R> {

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
      log.debug("Resource not found in temporal cache reading it from informer cache," +
          " for Resource ID: {}", resourceID);
      return super.get(resourceID);
    }
  }

  @Override
  public Optional<R> getCachedValue(ResourceID resourceID) {
    return get(resourceID);
  }

  @Override
  public Stream<R> list(String namespace, Predicate<R> predicate) {
    return manager().list(namespace, predicate);
  }

  void setTemporalResourceCache(TemporaryResourceCache<R> temporaryResourceCache) {
    this.temporaryResourceCache = temporaryResourceCache;
  }

  public void addIndexers(Map<String, Function<R, List<String>>> indexers) {
    manager().addIndexers(indexers);
  }

  public List<R> byIndex(String indexName, String indexKey) {
    return manager().byIndex(indexName, indexKey);
  }
}
