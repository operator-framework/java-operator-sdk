package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * This is a special case since is not bound to a single custom resource
 */
public class ControllerResourceEventSource<T extends HasMetadata> extends AbstractEventSource
    implements ResourceEventHandler<T> {

  public static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";

  private static final Logger log = LoggerFactory.getLogger(ControllerResourceEventSource.class);

  private final Controller<T> controller;
  private final Map<String, SharedIndexInformer<T>> sharedIndexInformers =
      new ConcurrentHashMap<>();

  private final ResourceEventFilter<T> filter;
  private final OnceWhitelistEventFilterEventFilter<T> onceWhitelistEventFilterEventFilter;
  private final ControllerResourceCache<T> cache;

  public ControllerResourceEventSource(Controller<T> controller) {
    this.controller = controller;
    var cloner = controller.getConfiguration().getConfigurationService().getResourceCloner();
    this.cache = new ControllerResourceCache<>(sharedIndexInformers, cloner);

    var filters = new ResourceEventFilter[] {
        ResourceEventFilters.finalizerNeededAndApplied(),
        ResourceEventFilters.markedForDeletion(),
        ResourceEventFilters.and(
            controller.getConfiguration().getEventFilter(),
            ResourceEventFilters.generationAware()),
        null
    };

    if (controller.getConfiguration().isGenerationAware()) {
      onceWhitelistEventFilterEventFilter = new OnceWhitelistEventFilterEventFilter<>();
      filters[filters.length - 1] = onceWhitelistEventFilterEventFilter;
    } else {
      onceWhitelistEventFilterEventFilter = null;
    }
    filter = ResourceEventFilters.or(filters);
  }

  @Override
  public void start() {
    final var configuration = controller.getConfiguration();
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var client = controller.getCRClient();
    final var labelSelector = configuration.getLabelSelector();

    try {
      if (ControllerConfiguration.allNamespacesWatched(targetNamespaces)) {
        final var filteredBySelectorClient = client.inAnyNamespace()
            .withLabelSelector(labelSelector);
        final var informer =
            createAndRunInformerFor(filteredBySelectorClient, ANY_NAMESPACE_MAP_KEY);
        log.debug("Registered {} -> {} for any namespace", controller, informer);
      } else {
        targetNamespaces.forEach(
            ns -> {
              final var informer = createAndRunInformerFor(
                  client.inNamespace(ns).withLabelSelector(labelSelector), ns);
              log.debug("Registered {} -> {} for namespace: {}", controller, informer,
                  ns);
            });
      }
    } catch (Exception e) {
      if (e instanceof KubernetesClientException) {
        KubernetesClientException ke = (KubernetesClientException) e;
        if (404 == ke.getCode()) {
          // only throw MissingCRDException if the 404 error occurs on the target CRD
          final var targetCRDName = controller.getConfiguration().getResourceTypeName();
          if (targetCRDName.equals(ke.getFullResourceName())) {
            throw new MissingCRDException(targetCRDName, null, e.getMessage(), e);
          }
        }
      }
      throw e;
    }
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
  }

  public void eventReceived(ResourceAction action, T customResource, T oldResource) {
    try {
      log.debug(
          "Event received for resource: {}", getName(customResource));
      MDCUtils.addResourceInfo(customResource);
      if (filter.acceptChange(controller.getConfiguration(), oldResource, customResource)) {
        eventHandler.handleEvent(
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

  public Optional<T> get(ResourceID resourceID) {
    return cache.get(resourceID);
  }

  public ControllerResourceCache<T> getResourceCache() {
    return cache;
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

  /**
   * This will ensure that the next event received after this method is called will not be filtered
   * out.
   *
   * @param resourceID - to which the event is related
   */
  public void whitelistNextEvent(ResourceID resourceID) {
    if (onceWhitelistEventFilterEventFilter != null) {
      onceWhitelistEventFilterEventFilter.whitelistNextEvent(resourceID);
    }
  }

}
