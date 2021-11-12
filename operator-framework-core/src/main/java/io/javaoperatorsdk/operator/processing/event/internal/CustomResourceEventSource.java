package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;
import static io.javaoperatorsdk.operator.processing.event.internal.LabelSelectorParser.parseSimpleLabelSelector;

/**
 * This is a special case since is not bound to a single custom resource
 */
public class CustomResourceEventSource<T extends CustomResource<?, ?>> extends AbstractEventSource
    implements ResourceEventHandler<T>, ResourceCache<T> {

  public static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";

  private static final Logger log = LoggerFactory.getLogger(CustomResourceEventSource.class);

  private final Controller<T> controller;
  private final Map<String, SharedIndexInformer<T>> sharedIndexInformers =
      new ConcurrentHashMap<>();

  private final CustomResourceEventFilter<T> filter;
  private final OnceWhitelistEventFilterEventFilter<T> onceWhitelistEventFilterEventFilter;
  private final Cloner cloner;

  public CustomResourceEventSource(Controller<T> controller) {
    this.controller = controller;
    this.cloner = controller.getConfiguration().getConfigurationService().getResourceCloner();

    var filters = new CustomResourceEventFilter[] {
        CustomResourceEventFilters.finalizerNeededAndApplied(),
        CustomResourceEventFilters.markedForDeletion(),
        CustomResourceEventFilters.and(
            controller.getConfiguration().getEventFilter(),
            CustomResourceEventFilters.generationAware()),
        null
    };

    if (controller.getConfiguration().isGenerationAware()) {
      onceWhitelistEventFilterEventFilter = new OnceWhitelistEventFilterEventFilter<>();
      filters[filters.length - 1] = onceWhitelistEventFilterEventFilter;
    } else {
      onceWhitelistEventFilterEventFilter = null;
    }
    filter = CustomResourceEventFilters.or(filters);
  }

  @Override
  public void start() {
    final var configuration = controller.getConfiguration();
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var client = controller.getCRClient();
    final var labelSelector = configuration.getLabelSelector();

    try {
      if (ControllerConfiguration.allNamespacesWatched(targetNamespaces)) {
        var informer = client.inAnyNamespace()
            .withLabels(parseSimpleLabelSelector(labelSelector)).runnableInformer(0);
        informer.addEventHandler(this);
        sharedIndexInformers.put(ANY_NAMESPACE_MAP_KEY, informer);
        log.debug("Registered {} -> {} for any namespace", controller, informer);
        informer.run();
      } else {
        targetNamespaces.forEach(
            ns -> {
              var informer = client.inNamespace(ns)
                  .withLabels(parseSimpleLabelSelector(labelSelector)).runnableInformer(0);
              informer.addEventHandler(this);
              sharedIndexInformers.put(ns, informer);
              informer.run();
              log.debug("Registered {} -> {} for namespace: {}", controller, informer,
                  ns);
            });
      }
    } catch (Exception e) {
      if (e instanceof KubernetesClientException) {
        KubernetesClientException ke = (KubernetesClientException) e;
        if (404 == ke.getCode()) {
          // only throw MissingCRDException if the 404 error occurs on the target CRD
          final var targetCRDName = controller.getConfiguration().getCRDName();
          if (targetCRDName.equals(ke.getFullResourceName())) {
            throw new MissingCRDException(targetCRDName, null, e.getMessage(), e);
          }
        }
      }
      throw e;
    }
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
      MDCUtils.addCustomResourceInfo(customResource);
      if (filter.acceptChange(controller.getConfiguration(), oldResource, customResource)) {
        eventHandler.handleEvent(
            new CustomResourceEvent(action, CustomResourceID.fromResource(customResource)));
      } else {
        log.debug(
            "Skipping event handling resource {} with version: {}",
            getUID(customResource),
            getVersion(customResource));
      }
    } finally {
      MDCUtils.removeCustomResourceInfo();
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
  public Optional<T> getCustomResource(CustomResourceID resourceID) {
    var sharedIndexInformer =
        sharedIndexInformers.get(resourceID.getNamespace().orElse(ANY_NAMESPACE_MAP_KEY));
    var resource = sharedIndexInformer.getStore()
        .getByKey(Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null),
            resourceID.getName()));
    if (resource == null) {
      return Optional.empty();
    } else {
      return Optional.of((T) (cloner.clone(resource)));
    }
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
   * @param customResourceID - to which the event is related
   */
  public void whitelistNextEvent(CustomResourceID customResourceID) {
    if (onceWhitelistEventFilterEventFilter != null) {
      onceWhitelistEventFilterEventFilter.whitelistNextEvent(customResourceID);
    }
  }

}
