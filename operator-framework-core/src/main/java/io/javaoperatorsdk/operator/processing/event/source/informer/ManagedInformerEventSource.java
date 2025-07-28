package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Informable;
import io.javaoperatorsdk.operator.api.config.NamespaceChangeable;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.health.InformerWrappingEventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.*;

@SuppressWarnings("rawtypes")
public abstract class ManagedInformerEventSource<
        R extends HasMetadata, P extends HasMetadata, C extends Informable<R>>
    extends AbstractEventSource<R, P>
    implements ResourceEventHandler<R>,
        Cache<R>,
        IndexerResourceCache<R>,
        RecentOperationCacheFiller<R>,
        NamespaceChangeable,
        InformerWrappingEventSourceHealthIndicator<R>,
        Configurable<C> {

  private static final Logger log = LoggerFactory.getLogger(ManagedInformerEventSource.class);
  private InformerManager<R, C> cache;
  protected final boolean parseResourceVersions;
  private ControllerConfiguration<R> controllerConfiguration;
  private final C configuration;
  private final Map<String, Function<R, List<String>>> indexers = new HashMap<>();
  protected TemporaryResourceCache<R> temporaryResourceCache;
  protected MixedOperation client;

  protected ManagedInformerEventSource(
      String name, MixedOperation client, C configuration, boolean parseResourceVersions) {
    super(configuration.getResourceClass(), name);
    this.parseResourceVersions = parseResourceVersions;
    this.client = client;
    this.configuration = configuration;
  }

  @Override
  public void onAdd(R resource) {
    temporaryResourceCache.onAddOrUpdateEvent(resource);
  }

  @Override
  public void onUpdate(R oldObj, R newObj) {
    temporaryResourceCache.onAddOrUpdateEvent(newObj);
  }

  @Override
  public void onDelete(R obj, boolean deletedFinalStateUnknown) {
    temporaryResourceCache.onDeleteEvent(obj, deletedFinalStateUnknown);
  }

  protected InformerManager<R, C> manager() {
    return cache;
  }

  @Override
  public void changeNamespaces(Set<String> namespaces) {
    if (allowsNamespaceChanges()) {
      manager().changeNamespaces(namespaces);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public synchronized void start() {
    if (isRunning()) {
      return;
    }
    temporaryResourceCache = temporaryResourceCache();
    this.cache = new InformerManager<>(client, configuration, this);
    cache.setControllerConfiguration(controllerConfiguration);
    cache.addIndexers(indexers);
    manager().start();
    super.start();
  }

  @Override
  public synchronized void stop() {
    if (!isRunning()) {
      return;
    }
    super.stop();
    manager().stop();
  }

  @Override
  public void handleRecentResourceUpdate(
      ResourceID resourceID, R resource, R previousVersionOfResource) {
    temporaryResourceCache.putResource(
        resource, previousVersionOfResource.getMetadata().getResourceVersion());
  }

  @Override
  public void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    temporaryResourceCache.putAddedResource(resource);
  }

  @Override
  public Optional<R> get(ResourceID resourceID) {
    Optional<R> resource = temporaryResourceCache.getResourceFromCache(resourceID);
    if (resource.isPresent()) {
      log.debug("Resource found in temporary cache for Resource ID: {}", resourceID);
      return resource;
    } else {
      log.debug(
          "Resource not found in temporary cache reading it from informer cache,"
              + " for Resource ID: {}",
          resourceID);
      var res = cache.get(resourceID);
      log.debug("Resource found in cache: {} for id: {}", res.isPresent(), resourceID);
      return res;
    }
  }

  protected TemporaryResourceCache temporaryResourceCache() {
    return new TemporaryResourceCache<>(
        this, NOOPTemporalPrimaryToSecondaryIndex.getInstance(), parseResourceVersions);
  }

  @SuppressWarnings("unused")
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

  @Override
  public void addIndexers(Map<String, Function<R, List<String>>> indexers) {
    if (isRunning()) {
      throw new OperatorException("Cannot add indexers after InformerEventSource started.");
    }
    this.indexers.putAll(indexers);
  }

  @Override
  public List<R> byIndex(String indexName, String indexKey) {
    return manager().byIndex(indexName, indexKey);
  }

  @Override
  public Stream<ResourceID> keys() {
    return cache.keys();
  }

  @Override
  public Stream<R> list(Predicate<R> predicate) {
    return cache.list(predicate);
  }

  @Override
  public Map<String, InformerHealthIndicator> informerHealthIndicators() {
    return cache.informerHealthIndicators();
  }

  @Override
  public Status getStatus() {
    return InformerWrappingEventSourceHealthIndicator.super.getStatus();
  }

  @Override
  public C configuration() {
    return configuration;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "resourceClass: "
        + configuration().getResourceClass().getSimpleName()
        + "}";
  }

  public void setControllerConfiguration(ControllerConfiguration<R> controllerConfiguration) {
    this.controllerConfiguration = controllerConfiguration;
  }
}
