package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

import static io.javaoperatorsdk.operator.ReconcilerUtils.handleKubernetesClientException;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public class ControllerResourceEventSource<T extends HasMetadata>
    extends AbstractResourceEventSource<T, T>
    implements ResourceEventHandler<T>, ResourceCache<T> {

  public static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";

  private static final Logger log = LoggerFactory.getLogger(ControllerResourceEventSource.class);

  private final Controller<T> controller;
  private final Map<String, SharedIndexInformer<T>> sharedIndexInformers =
      new ConcurrentHashMap<>();

  private final ResourceEventFilter<T> filter;
  private final ControllerResourceCache<T> cache;
  private final TemporaryResourceCache<T> temporaryResourceCache;

  @SuppressWarnings("unchecked")
  public ControllerResourceEventSource(Controller<T> controller) {
    super(controller.getConfiguration().getResourceClass());
    this.controller = controller;
    final var configurationService = controller.getConfiguration().getConfigurationService();
    var cloner = configurationService != null ? configurationService.getResourceCloner()
        : ConfigurationService.DEFAULT_CLONER;
    this.cache = new ControllerResourceCache<>(sharedIndexInformers, cloner);
    temporaryResourceCache = new TemporaryResourceCache<>(cache);
    var filters = new ResourceEventFilter[] {
        ResourceEventFilters.finalizerNeededAndApplied(),
        ResourceEventFilters.markedForDeletion(),
        ResourceEventFilters.generationAware()
    };
    if (controller.getConfiguration().getEventFilter() != null) {
      filter = controller.getConfiguration().getEventFilter().and(ResourceEventFilters.or(filters));
    } else {
      filter = ResourceEventFilters.or(filters);
    }
  }

  @Override
  public void start() {
    final var configuration = controller.getConfiguration();
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var client = controller.getCRClient();
    final var labelSelector = configuration.getLabelSelector();

    try {
      if (ControllerConfiguration.allNamespacesWatched(targetNamespaces)) {
        final var informer =
            createAndRunInformerFor(client.inAnyNamespace()
                .withLabelSelector(labelSelector), ANY_NAMESPACE_MAP_KEY);
        log.debug("Registered {} -> {} for any namespace", controller, informer);
      } else {
        targetNamespaces.forEach(ns -> {
          final var informer = createAndRunInformerFor(
              client.inNamespace(ns).withLabelSelector(labelSelector), ns);
          log.debug("Registered {} -> {} for namespace: {}", controller, informer, ns);
        });
      }
    } catch (Exception e) {
      handleKubernetesClientException(e, controller.getConfiguration().getResourceTypeName());
      throw e;
    }
    super.start();
  }

  private SharedIndexInformer<T> createAndRunInformerFor(
      FilterWatchListDeletable<T, KubernetesResourceList<T>> filteredBySelectorClient, String key) {
    var informer = filteredBySelectorClient.runnableInformer(0);
    informer.addEventHandler(this);
    sharedIndexInformers.put(key, informer);
    informer.run();
    return informer;
  }

  @Override
  public void stop() {
    for (SharedIndexInformer<T> informer : sharedIndexInformers.values()) {
      try {
        log.info("Stopping informer {} -> {}", controller, informer);
        informer.stop();
      } catch (Exception e) {
        log.warn("Error stopping informer {} -> {}", controller, informer, e);
      }
    }
    super.stop();
  }

  public void eventReceived(ResourceAction action, T customResource, T oldResource) {
    try {
      log.debug(
          "Event received for resource: {}", getName(customResource));
      temporaryResourceCache.removeResourceFromCache(customResource);
      MDCUtils.addResourceInfo(customResource);
      controller.getEventSourceManager().broadcastOnResourceEvent(action, customResource,
          oldResource);
      if (filter.acceptChange(controller.getConfiguration(), oldResource, customResource)) {
        getEventHandler().handleEvent(
            new ResourceEvent(action, ResourceID.fromResource(customResource)));
      } else {
        log.debug(
            "Skipping event handling resource {} with version: {}",
            getUID(customResource),
            getVersion(customResource));
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  @Override
  public void onAdd(T resource) {
    eventReceived(ResourceAction.ADDED, resource, null);
  }

  @Override
  public void onUpdate(T oldCustomResource, T newCustomResource) {
    eventReceived(ResourceAction.UPDATED, newCustomResource, oldCustomResource);
  }

  @Override
  public void onDelete(T resource, boolean b) {
    eventReceived(ResourceAction.DELETED, resource, null);
  }


  @Override
  public Optional<T> get(ResourceID resourceID) {
    Optional<T> resource = temporaryResourceCache.getResourceFromCache(resourceID);
    if (resource.isPresent()) {
      log.debug("Resource found in temporal cache for Resource ID: {}", resourceID);
      return resource;
    } else {
      return cache.get(resourceID);
    }
  }

  @Override
  public Stream<ResourceID> keys() {
    return cache.keys();
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return cache.list(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    return cache.list(namespace, predicate);
  }

  /**
   * @return shared informers by namespace. If custom resource is not namespace scoped use
   *         CustomResourceEventSource.ANY_NAMESPACE_MAP_KEY
   */
  public Map<String, SharedIndexInformer<T>> getInformers() {
    return Collections.unmodifiableMap(sharedIndexInformers);
  }

  public SharedIndexInformer<T> getInformer(String namespace) {
    return getInformers().get(Objects.requireNonNullElse(namespace, ANY_NAMESPACE_MAP_KEY));
  }

  @Override
  public Optional<T> getAssociated(T primary) {
    return get(ResourceID.fromResource(primary));
  }

  public void handleRecentResourceUpdate(T resource,
      T previousResourceVersion) {
    temporaryResourceCache.putUpdatedResource(resource,
        previousResourceVersion.getMetadata().getResourceVersion());
  }

}
