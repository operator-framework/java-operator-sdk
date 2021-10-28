package io.javaoperatorsdk.operator.processing.event.internal;

import java.io.IOException;
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
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

  private final ConfiguredController<T> controller;
  private final Map<String, SharedIndexInformer<T>> sharedIndexInformers =
      new ConcurrentHashMap<>();
  private final ObjectMapper cloningObjectMapper;

  public CustomResourceEventSource(ConfiguredController<T> controller) {
    this.controller = controller;
    this.cloningObjectMapper =
        controller.getConfiguration().getConfigurationService().getObjectMapper();
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
            .withLabels(parseSimpleLabelSelector(labelSelector)).inform(this);
        sharedIndexInformers.put(ANY_NAMESPACE_MAP_KEY, informer);
        log.debug("Registered {} -> {} for any namespace", controller, informer);
      } else {
        targetNamespaces.forEach(
            ns -> {
              var informer = client.inNamespace(ns)
                  .withLabels(parseSimpleLabelSelector(labelSelector)).inform(this);
              sharedIndexInformers.put(ns, informer);
              log.debug("Registered {} -> {} for namespace: {}", controller, informer,
                  ns);
            });
      }
    } catch (Exception e) {
      // todo double check this if still applies for informers
      if (e instanceof KubernetesClientException) {
        KubernetesClientException ke = (KubernetesClientException) e;
        if (404 == ke.getCode()) {
          // only throw MissingCRDException if the 404 error occurs on the target CRD
          final var targetCRDName = controller.getConfiguration().getCRDName();
          if (targetCRDName.equals(ke.getFullResourceName())) {
            throw new MissingCRDException(targetCRDName, null);
          }
        }
      }
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    eventHandler.close();
    for (SharedIndexInformer informer : sharedIndexInformers.values()) {
      try {
        log.info("Closing informer {} -> {}", controller, informer);
        informer.close();
      } catch (Exception e) {
        log.warn("Error closing informer {} -> {}", controller, informer, e);
      }
    }
  }

  public void eventReceived(ResourceAction action, T customResource, T oldResource) {
    log.debug(
        "Event received for resource: {}", getName(customResource));

    final CustomResourceEventFilter<T> filter = CustomResourceEventFilters.or(
        CustomResourceEventFilters.finalizerNeededAndApplied(),
        CustomResourceEventFilters.markedForDeletion(),
        CustomResourceEventFilters.and(
            controller.getConfiguration().getEventFilter(),
            CustomResourceEventFilters.generationAware()));

    if (filter.acceptChange(controller.getConfiguration(), oldResource, customResource)) {
      eventHandler.handleEvent(
          new CustomResourceEvent(action, CustomResourceID.fromResource(customResource)));
    } else {
      log.debug(
          "Skipping event handling resource {} with version: {}",
          getUID(customResource),
          getVersion(customResource));
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
      return Optional.of(clone(resource));
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

  private T clone(T customResource) {
    try {
      return (T) cloningObjectMapper.readValue(
          cloningObjectMapper.writeValueAsString(customResource), customResource.getClass());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
