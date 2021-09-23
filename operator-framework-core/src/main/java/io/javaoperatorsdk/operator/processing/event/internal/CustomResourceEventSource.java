package io.javaoperatorsdk.operator.processing.event.internal;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

/** This is a special case since is not bound to a single custom resource */
public class CustomResourceEventSource<T extends CustomResource<?, ?>> extends AbstractEventSource
    implements Watcher<T> {

  private static final Logger log = LoggerFactory.getLogger(CustomResourceEventSource.class);

  private final ConfiguredController<T> controller;
  private final Map<String, Long> lastGenerationProcessedSuccessfully = new ConcurrentHashMap<>();
  private final List<Watch> watches;
  private final CustomResourceCache customResourceCache;

  public CustomResourceEventSource(ConfiguredController<T> controller) {
    this.controller = controller;
    this.watches = new LinkedList<>();
    this.customResourceCache = new CustomResourceCache(
        controller.getConfiguration().getConfigurationService().getObjectMapper());
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

    if (!skipBecauseOfGeneration(customResource)) {
      eventHandler.handleEvent(new CustomResourceEvent(action, customResource, this));
      markLastGenerationProcessed(customResource);
    } else {
      log.debug(
          "Skipping event handling resource {} with version: {}",
          getUID(customResource),
          getVersion(customResource));
    }
  }

  private void markLastGenerationProcessed(T resource) {
    if (controller.getConfiguration().isGenerationAware()
        && resource.hasFinalizer(controller.getConfiguration().getFinalizer())) {
      lastGenerationProcessedSuccessfully.put(
          KubernetesResourceUtils.getUID(resource), resource.getMetadata().getGeneration());
    }
  }

  private boolean skipBecauseOfGeneration(T customResource) {
    if (!controller.getConfiguration().isGenerationAware()) {
      return false;
    }
    // if CR being deleted generation is naturally not changing, so we process all the events
    if (customResource.isMarkedForDeletion()) {
      return false;
    }

    // only proceed if we haven't already seen this custom resource generation
    Long lastGeneration =
        lastGenerationProcessedSuccessfully.get(customResource.getMetadata().getUid());
    if (lastGeneration == null) {
      return false;
    } else {
      return customResource.getMetadata().getGeneration() <= lastGeneration;
    }
  }

  @Override
  public void eventSourceDeRegisteredForResource(String customResourceUid) {
    lastGenerationProcessedSuccessfully.remove(customResourceUid);
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
  public CustomResourceCache getCache() {
    return customResourceCache;
  }
}
