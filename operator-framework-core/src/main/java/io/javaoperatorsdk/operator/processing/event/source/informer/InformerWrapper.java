package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

class InformerWrapper<T extends HasMetadata>
    implements LifecycleAware, IndexerResourceCache<T> {

  private final SharedIndexInformer<T> informer;
  private final Cache<T> cache;

  public InformerWrapper(SharedIndexInformer<T> informer) {
    this.informer = informer;
    this.cache = (Cache<T>) informer.getStore();
  }

  @Override
  public void start() throws OperatorException {
    try {
      informer.run();

      // register stopped handler if we have one defined
      ConfigurationServiceProvider.instance().getInformerStoppedHandler()
          .ifPresent(ish -> {
            final var stopped = informer.stopped();
            if (stopped != null) {
              stopped.handle((res, ex) -> {
                ish.onStop(informer, ex);
                return null;
              });
            } else {
              final var apiTypeClass = informer.getApiTypeClass();
              final var fullResourceName =
                  HasMetadata.getFullResourceName(apiTypeClass);
              final var version = HasMetadata.getVersion(apiTypeClass);
              throw new IllegalStateException(
                  "Cannot retrieve 'stopped' callback to listen to informer stopping for informer for "
                      + fullResourceName + "/" + version);
            }
          });

    } catch (Exception e) {
      ReconcilerUtils.handleKubernetesClientException(e,
          HasMetadata.getFullResourceName(informer.getApiTypeClass()));
      throw e;
    }
  }

  @Override
  public void stop() throws OperatorException {
    informer.stop();
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return Optional.ofNullable(cache.getByKey(getKey(resourceID)));
  }

  private String getKey(ResourceID resourceID) {
    return Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null), resourceID.getName());
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return cache.list().stream().filter(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    final var stream = cache.list().stream()
        .filter(r -> namespace.equals(r.getMetadata().getNamespace()));
    return predicate != null ? stream.filter(predicate) : stream;
  }

  @Override
  public Stream<ResourceID> keys() {
    return cache.listKeys().stream().map(Mappers::fromString);
  }

  public void addEventHandler(ResourceEventHandler<T> eventHandler) {
    informer.addEventHandler(eventHandler);
  }

  @Override
  public void addIndexers(Map<String, Function<T, List<String>>> indexers) {
    informer.getIndexer().addIndexers(indexers);
  }

  @Override
  public List<T> byIndex(String indexName, String indexKey) {
    return informer.getIndexer().byIndex(indexName, indexKey);
  }
}
