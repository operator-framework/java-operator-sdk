package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public abstract class AbstractResourceEventSource<T extends HasMetadata, U extends ResourceConfiguration<T, U>, V extends EventSourceWrapper<T>, P extends HasMetadata>
    extends AbstractEventSource<P>
    implements ResourceEventHandler<T> {

  private static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";
  private static final Logger log = LoggerFactory.getLogger(AbstractResourceEventSource.class);

  private final Map<String, V> sources = new ConcurrentHashMap<>();
  private final ResourceEventFilter<T, U> filter;
  private final U configuration;
  private final MixedOperation<T, KubernetesResourceList<T>, Resource<T>> client;
  private final Cloner cloner;
  private final ResourceCache<T> cache;

  public AbstractResourceEventSource(U configuration,
      MixedOperation<T, KubernetesResourceList<T>, Resource<T>> client, Cloner cloner,
      EventSourceRegistry<P> registry) {
    super(configuration.getResourceClass());
    this.configuration = configuration;
    this.client = client;
    this.filter = initFilter(configuration);
    this.cloner = cloner;
    setEventRegistry(registry);

    initSources();

    this.cache = new AggregateResourceCache<>(sources);
  }

  public U getConfiguration() {
    return configuration;
  }

  protected abstract ResourceEventFilter<T, U> initFilter(U configuration);

  protected abstract V wrapEventSource(
      FilterWatchListDeletable<T, KubernetesResourceList<T>> filteredBySelectorClient,
      Cloner cloner);

  void eventReceived(ResourceAction action, T resource, T oldResource) {
    log.debug("Event received for resource: {}", getName(resource));
    if (filter.acceptChange(configuration, oldResource, resource)) {
      getEventHandler().handleEvent(new ResourceEvent(action, ResourceID.fromResource(resource)));
    } else {
      log.debug(
          "Skipping event handling resource {} with version: {}",
          getUID(resource),
          getVersion(resource));
    }
  }

  @Override
  public void onAdd(T resource) {
    eventReceived(ResourceAction.ADDED, resource, null);
  }

  @Override
  public void onUpdate(T oldResource, T newResource) {
    eventReceived(ResourceAction.UPDATED, newResource, oldResource);
  }

  @Override
  public void onDelete(T resource, boolean b) {
    eventReceived(ResourceAction.DELETED, resource, null);
  }

  @Override
  public void start() throws OperatorException {
    sources.values().parallelStream().forEach(LifecycleAware::start);
  }

  private void initSources() {
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var labelSelector = configuration.getLabelSelector();

    if (ResourceConfiguration.allNamespacesWatched(targetNamespaces)) {
      final var filteredBySelectorClient =
          client.inAnyNamespace().withLabelSelector(labelSelector);
      final var source = createEventSource(filteredBySelectorClient, ANY_NAMESPACE_MAP_KEY);
      log.debug("Registered {} -> {} for any namespace", this, source);
    } else {
      targetNamespaces.forEach(
          ns -> {
            final var source =
                createEventSource(client.inNamespace(ns).withLabelSelector(labelSelector), ns);
            log.debug("Registered {} -> {} for namespace: {}", this, source,
                ns);
          });
    }
  }


  private V createEventSource(
      FilterWatchListDeletable<T, KubernetesResourceList<T>> filteredBySelectorClient, String key) {
    final var source = wrapEventSource(filteredBySelectorClient, cloner);
    sources.put(key, source);
    return source;
  }

  @Override
  public void stop() {
    for (V source : sources.values()) {
      try {
        log.info("Stopping informer {} -> {}", this, source);
        source.stop();
      } catch (Exception e) {
        log.warn("Error stopping informer {} -> {}", this, source, e);
      }
    }
  }

  public ResourceCache<T> getResourceCache() {
    return cache;
  }
}
