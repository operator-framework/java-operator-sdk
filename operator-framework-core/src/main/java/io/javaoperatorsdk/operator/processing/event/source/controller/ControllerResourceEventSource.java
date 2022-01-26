package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractResourceEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public class ControllerResourceEventSource<T extends HasMetadata>
    extends AbstractResourceEventSource<T, T>
    implements ResourceEventHandler<GenericKubernetesResource> {

  public static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";

  private static final Logger log = LoggerFactory.getLogger(ControllerResourceEventSource.class);

  private final Controller<T> controller;
  private final Map<String, SharedIndexInformer<GenericKubernetesResource>> sharedIndexInformers =
      new ConcurrentHashMap<>();

  private final ResourceEventFilter<T> filter;
  private final OnceWhitelistEventFilterEventFilter<T> onceWhitelistEventFilterEventFilter;
  private final ControllerResourceCache<T> cache;
  private final String CRVersion;

  public ControllerResourceEventSource(Controller<T> controller) {
    super(controller.getConfiguration().getResourceClass());
    this.controller = controller;
    final var configurationService = controller.getConfiguration().getConfigurationService();
    var cloner = configurationService != null ? configurationService.getResourceCloner()
        : ConfigurationService.DEFAULT_CLONER;
    this.cache = new ControllerResourceCache<T>(sharedIndexInformers, cloner);

    var filters = new ResourceEventFilter[] {
        ResourceEventFilters.finalizerNeededAndApplied(),
        ResourceEventFilters.markedForDeletion(),
        ResourceEventFilters.generationAware(),
        null
    };

    if (controller.getConfiguration().isGenerationAware()) {
      onceWhitelistEventFilterEventFilter = new OnceWhitelistEventFilterEventFilter<>();
      filters[filters.length - 1] = onceWhitelistEventFilterEventFilter;
    } else {
      onceWhitelistEventFilterEventFilter = null;
    }
    if (controller.getConfiguration().getEventFilter() != null) {
      filter = controller.getConfiguration().getEventFilter().and(ResourceEventFilters.or(filters));
    } else {
      filter = ResourceEventFilters.or(filters);
    }

    var resourceClass = controller.getConfiguration().getResourceClass();
    // TODO: check if we should use: HasMetadata.getFullResourceName(resourceClass);
    this.CRVersion =
        HasMetadata.getGroup(resourceClass) + "/" + HasMetadata.getVersion(resourceClass);
  }

  @Override
  public void start() {
    final var configuration = controller.getConfiguration();
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var client = controller.getGenericClient();
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
      if (e instanceof KubernetesClientException) {
        handleKubernetesClientException(e);
      }
      throw e;
    }
    super.start();
  }

  private SharedIndexInformer<GenericKubernetesResource> createAndRunInformerFor(
      FilterWatchListDeletable<GenericKubernetesResource, GenericKubernetesResourceList> filteredBySelectorClient,
      String key) {
    var informer = filteredBySelectorClient.runnableInformer(0);
    informer.addEventHandler(this);
    sharedIndexInformers.put(key, informer);
    informer.run();
    return informer;
  }

  @Override
  public void stop() {
    for (SharedIndexInformer<GenericKubernetesResource> informer : sharedIndexInformers.values()) {
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

  private String extractUnderlyingCRVersion(GenericKubernetesResource resource) {
    return resource
        .getMetadata()
        .getManagedFields()
        .get(0)
        .getApiVersion();
  }

  @Override
  public void onAdd(GenericKubernetesResource genericKubernetesResource) {
    if (CRVersion.equals(extractUnderlyingCRVersion(genericKubernetesResource))) {
      var resource = Serialization.unmarshal(
          Serialization.asJson(genericKubernetesResource), this.getResourceClass());
      eventReceived(ResourceAction.ADDED, resource, null);
    }
  }

  @Override
  public void onUpdate(GenericKubernetesResource oldResource,
      GenericKubernetesResource newResource) {
    if (CRVersion.equals(extractUnderlyingCRVersion(newResource))) {
      var newCustomResource = Serialization.unmarshal(
          Serialization.asJson(newResource), this.getResourceClass());

      // Best effort try to deserialize the old CR with the same deserializer as the new
      T oldCustomResource = null;
      try {
        oldCustomResource = Serialization.unmarshal(
            Serialization.asJson(newResource), this.getResourceClass());
      } catch (Exception e) {
        // ignored
      }
      eventReceived(ResourceAction.UPDATED, newCustomResource, oldCustomResource);
    }
  }

  @Override
  public void onDelete(GenericKubernetesResource genericKubernetesResource, boolean b) {
    if (CRVersion.equals(extractUnderlyingCRVersion(genericKubernetesResource))) {
      var resource = Serialization.unmarshal(
          Serialization.asJson(genericKubernetesResource), this.getResourceClass());
      eventReceived(ResourceAction.DELETED, resource, null);
    }
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
  public Map<String, SharedIndexInformer<GenericKubernetesResource>> getInformers() {
    return Collections.unmodifiableMap(sharedIndexInformers);
  }

  public SharedIndexInformer<GenericKubernetesResource> getInformer(String namespace) {
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


  private void handleKubernetesClientException(Exception e) {
    KubernetesClientException ke = (KubernetesClientException) e;
    if (404 == ke.getCode()) {
      // only throw MissingCRDException if the 404 error occurs on the target CRD
      final var targetCRDName = controller.getConfiguration().getResourceTypeName();
      if (targetCRDName.equals(ke.getFullResourceName())) {
        throw new MissingCRDException(targetCRDName, null, e.getMessage(), e);
      }
    }
  }

  @Override
  public Optional<T> getAssociated(T primary) {
    return cache.get(ResourceID.fromResource(primary));
  }

}
