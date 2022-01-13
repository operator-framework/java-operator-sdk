package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
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
    implements ResourceEventHandler<T> {

  public static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";

  private static final Logger log = LoggerFactory.getLogger(ControllerResourceEventSource.class);

  private final Controller<T> controller;
  private SharedIndexInformer<T> sharedIndexInformer;

  private final ResourceEventFilter<T> filter;
  private final OnceWhitelistEventFilterEventFilter<T> onceWhitelistEventFilterEventFilter;
  private final ControllerResourceCache<T> cache;

  public ControllerResourceEventSource(Controller<T> controller) {
    super(controller.getConfiguration().getResourceClass());
    this.controller = controller;
    final var configurationService = controller.getConfiguration().getConfigurationService();
    var cloner = configurationService != null ? configurationService.getResourceCloner()
        : ConfigurationService.DEFAULT_CLONER;
    this.cache = new ControllerResourceCache<>(sharedIndexInformer, cloner);

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
  }

  @Override
  public void start() {
    final var configuration = controller.getConfiguration();
    final var targetNamespace = configuration.watchedNamespace();
    final var client = controller.getCRClient();
    final var labelSelector = configuration.getLabelSelector();

    try {
      if (targetNamespace.equals(Constants.WATCH_ALL_NAMESPACE)) {
        sharedIndexInformer = createAndRunInformerFor(client.inAnyNamespace()
                .withLabelSelector(labelSelector));
        log.debug("Registered {} -> {} for any namespace", controller, sharedIndexInformer);
      } else {
          final var informer = createAndRunInformerFor(
              client.inNamespace(targetNamespace).withLabelSelector(labelSelector), ns);
          log.debug("Registered {} -> {} for namespace: {}", controller, informer, ns);
        }
      } catch (Exception e) {
      if (e instanceof KubernetesClientException) {
        handleKubernetesClientException(e);
      }
      throw e;
    }
    super.start();
  }

  private SharedIndexInformer<T> createAndRunInformerFor(
      FilterWatchListDeletable<T, KubernetesResourceList<T>> filteredBySelectorClient) {
    var informer = filteredBySelectorClient.runnableInformer(0);
    informer.addEventHandler(this);
    informer.run();
    return informer;
  }

  @Override
  public void stop() {
      try {
        log.info("Stopping informer {} -> {}", controller, sharedIndexInformer);
        sharedIndexInformer.stop();
      } catch (Exception e) {
        log.warn("Error stopping informer {} -> {}", controller, sharedIndexInformer, e);
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
  public SharedIndexInformer<T> getSharedInformer() {
    return sharedIndexInformer;
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
