package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.handleKubernetesClientException;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public class ControllerResourceEventSource<T extends HasMetadata>
    extends ManagedInformerEventSource<T, T, ControllerConfiguration<T>>
    implements ResourceEventHandler<T> {

  private static final Logger log = LoggerFactory.getLogger(ControllerResourceEventSource.class);

  private final Controller<T> controller;
  private final ResourceEventFilter<T> legacyFilters;

  @SuppressWarnings("unchecked")
  public ControllerResourceEventSource(Controller<T> controller) {
    super(controller.getCRClient(), controller.getConfiguration());
    this.controller = controller;
    var filters = new ResourceEventFilter[] {
        ResourceEventFilters.finalizerNeededAndApplied(),
        ResourceEventFilters.markedForDeletion(),
        ResourceEventFilters.generationAware(),
    };
    if (controller.getConfiguration().getEventFilter() != null) {
      legacyFilters =
          controller.getConfiguration().getEventFilter().and(ResourceEventFilters.or(filters));
    } else {
      legacyFilters = ResourceEventFilters.or(filters);
    }
    controller.getConfiguration().onAddFilter().ifPresent(this::setOnAddFilter);
    controller.getConfiguration().onUpdateFilter().ifPresent(this::setOnUpdateFilter);
  }

  @Override
  public void start() {
    try {
      super.start();
    } catch (KubernetesClientException e) {
      handleKubernetesClientException(e, controller.getConfiguration().getResourceTypeName());
      throw e;
    }
  }

  public void eventReceived(ResourceAction action, T resource, T oldResource) {
    try {
      log.debug("Event received for resource: {}", getName(resource));
      MDCUtils.addResourceInfo(resource);
      controller.getEventSourceManager().broadcastOnResourceEvent(action, resource, oldResource);
      if (legacyFilters.acceptChange(controller, oldResource, resource)
          && acceptFilters(action, resource, oldResource)) {
        getEventHandler().handleEvent(
            new ResourceEvent(action, ResourceID.fromResource(resource), resource));
      } else {
        log.debug("Skipping event handling resource {} with version: {}", getUID(resource),
            getVersion(resource));
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  private boolean acceptFilters(ResourceAction action, T resource, T oldResource) {
    // delete event not filtered, there is no reconciliation for delete anyways
    switch (action) {
      case ADDED:
        return onAddFilter == null || onAddFilter.test(resource);
      case UPDATED:
        return onUpdateFilter == null || onUpdateFilter.test(resource, oldResource);
    }
    return true;
  }

  @Override
  public void onAdd(T resource) {
    super.onAdd(resource);
    eventReceived(ResourceAction.ADDED, resource, null);
  }

  @Override
  public void onUpdate(T oldCustomResource, T newCustomResource) {
    super.onUpdate(oldCustomResource, newCustomResource);
    eventReceived(ResourceAction.UPDATED, newCustomResource, oldCustomResource);
  }

  @Override
  public void onDelete(T resource, boolean b) {
    super.onDelete(resource, b);
    eventReceived(ResourceAction.DELETED, resource, null);
  }

  @Override
  public Optional<T> getSecondaryResource(T primary) {
    throw new IllegalStateException("This method should not be called here. Primary: " + primary);
  }

  @Override
  public Set<T> getSecondaryResources(T primary) {
    throw new IllegalStateException("This method should not be called here. Primary: " + primary);
  }

  @Override
  public void setOnDeleteFilter(BiPredicate<T, Boolean> onDeleteFilter) {
    throw new IllegalStateException(
        "onAddFilter is not supported for controller resource event source");
  }
}
