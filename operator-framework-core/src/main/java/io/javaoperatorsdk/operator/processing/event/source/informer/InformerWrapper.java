package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ExceptionHandler;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

class InformerWrapper<T extends HasMetadata>
    implements LifecycleAware, IndexerResourceCache<T>, InformerHealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(InformerWrapper.class);

  private final SharedIndexInformer<T> informer;
  private final Cache<T> cache;
  private final String namespaceIdentifier;

  public InformerWrapper(SharedIndexInformer<T> informer, String namespaceIdentifier) {
    this.informer = informer;
    this.namespaceIdentifier = namespaceIdentifier;
    this.cache = (Cache<T>) informer.getStore();

  }

  @Override
  public void start() throws OperatorException {
    try {
      var configService = ConfigurationServiceProvider.instance();
      // register stopped handler if we have one defined
      configService.getInformerStoppedHandler()
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
      if (!configService.stopOnInformerErrorDuringStartup()) {
        informer.exceptionHandler((b, t) -> !ExceptionHandler.isDeserializationException(t));
      }
      try {
        var start = informer.start();
        // note that in case we don't put here timeout and stopOnInformerErrorDuringStartup is
        // false, and there is a rbac issue the get never returns; therefore operator never really
        // starts
        start.toCompletableFuture().get(configService.cacheSyncTimeout().toMillis(),
            TimeUnit.MILLISECONDS);
      } catch (TimeoutException | ExecutionException e) {
        if (configService.stopOnInformerErrorDuringStartup()) {
          log.error("Informer startup error. Operator will be stopped. Informer: {}", informer, e);
          throw new OperatorException(e);
        } else {
          log.warn("Informer startup error. Will periodically retry. Informer: {}", informer, e);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }

    } catch (Exception e) {
      log.error("Couldn't start informer for " + versionedFullResourceName() + " resources", e);
      ReconcilerUtils.handleKubernetesClientException(e,
          HasMetadata.getFullResourceName(informer.getApiTypeClass()));
      throw e;
    }
  }

  private String versionedFullResourceName() {
    final var apiTypeClass = informer.getApiTypeClass();
    return ReconcilerUtils.getResourceTypeNameWithVersion(apiTypeClass);
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

  @Override
  public String toString() {
    return "InformerWrapper [" + versionedFullResourceName() + "] (" + informer + ')';
  }

  @Override
  public boolean hasSynced() {
    return informer.hasSynced();
  }

  @Override
  public boolean isWatching() {
    return informer.isWatching();
  }

  @Override
  public boolean isRunning() {
    return informer.isRunning();
  }

  @Override
  public String getTargetNamespace() {
    return namespaceIdentifier;
  }
}
