package io.javaoperatorsdk.operator.processing.event.internal;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.utils.Utils;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.CustomResourceCache;
import io.javaoperatorsdk.operator.processing.KubernetesResourceUtils;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * This is a special case since is not bound to a single custom resource
 */
public class CustomResourceEventSource<T extends CustomResource<?, ?>> extends AbstractEventSource
    implements Watcher<T> {

  private static final Logger log = LoggerFactory.getLogger(CustomResourceEventSource.class);

  private final ConfiguredController<T> controller;
  private final List<Watch> watches;
  private final CustomResourceCache<T> customResourceCache;

  public CustomResourceEventSource(ConfiguredController<T> controller) {
    this.controller = controller;
    this.watches = new LinkedList<>();

    this.customResourceCache = new CustomResourceCache<>(
        controller.getConfiguration().getConfigurationService().getObjectMapper(),
        controller.getConfiguration().getConfigurationService().getMetrics());
  }

  @Override
  public void start() {
    final var configuration = controller.getConfiguration();
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var client = controller.getCRClient();
    final var labelSelector = configuration.getLabelSelector();
    var options = new ListOptions();
    if (Utils.isNotNullOrEmpty(labelSelector)) {
      options.setLabelSelector(labelSelector);
    }

    try {
      if (ControllerConfiguration.allNamespacesWatched(targetNamespaces)) {
        var w = client.inAnyNamespace().watch(options, this);
        watches.add(w);
        log.debug("Registered {} -> {} for any namespace", controller, w);
      } else {
        targetNamespaces.forEach(
            ns -> {
              var w = client.inNamespace(ns).watch(options, this);
              watches.add(w);
              log.debug("Registered {} -> {} for namespace: {}", controller, w, ns);
            });
      }
    } catch (Exception e) {
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
    for (Watch watch : this.watches) {
      try {
        log.info("Closing watch {} -> {}", controller, watch);
        watch.close();
      } catch (Exception e) {
        log.warn("Error closing watcher {} -> {}", controller, watch, e);
      }
    }
  }

  @Override
  public void eventReceived(Watcher.Action action, T customResource) {
    log.debug(
        "Event received for action: {}, resource: {}", action.name(), getName(customResource));

    final String uuid = KubernetesResourceUtils.getUID(customResource);
    final T oldResource = customResourceCache.getLatestResource(uuid).orElse(null);

    // cache the latest version of the CR
    customResourceCache.cacheResource(customResource);

    if (action == Action.ERROR) {
      log.debug(
          "Skipping {} event for custom resource uid: {}, version: {}",
          action,
          getUID(customResource),
          getVersion(customResource));
      return;
    }

    final CustomResourceEventFilter<T> filter = CustomResourceEventFilters.or(
        CustomResourceEventFilters.finalizerNeededAndApplied(),
        CustomResourceEventFilters.markedForDeletion(),
        CustomResourceEventFilters.and(
            controller.getConfiguration().getEventFilter(),
            CustomResourceEventFilters.generationAware()));

    if (filter.acceptChange(controller.getConfiguration(), oldResource, customResource)) {
      eventHandler.handleEvent(new CustomResourceEvent(action, customResource, this));
    } else {
      log.debug(
          "Skipping event handling resource {} with version: {}",
          getUID(customResource),
          getVersion(customResource));
    }
  }

  @Override
  public void onClose(WatcherException e) {
    if (e == null) {
      return;
    }
    if (e.isHttpGone()) {
      log.warn("Received error for watch, will try to reconnect.", e);
      try {
        close();
        start();
      } catch (Throwable ex) {
        log.error("Unexpected error happened with watch reconnect. Will exit.", e);
        System.exit(1);
      }
    } else {
      // Note that this should not happen normally, since fabric8 client handles reconnect.
      // In case it tries to reconnect this method is not called.
      log.error("Unexpected error happened with watch. Will exit.", e);
      System.exit(1);
    }
  }

  // todo: remove
  public CustomResourceCache<T> getCache() {
    return customResourceCache;
  }
}
