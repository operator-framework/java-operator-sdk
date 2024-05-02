package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.handleKubernetesClientException;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;
import static io.javaoperatorsdk.operator.processing.event.source.controller.InternalEventFilters.*;

public class ControllerEventSource<T extends HasMetadata>
    extends ManagedInformerEventSource<T, T, ControllerConfiguration<T>>
    implements ResourceEventHandler<T> {

  private static final Logger log = LoggerFactory.getLogger(ControllerEventSource.class);
  public static final String NAME = "ControllerResourceEventSource";

  private final Controller<T> controller;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public ControllerEventSource(Controller<T> controller) {
    super(NAME, controller.getCRClient(), controller.getConfiguration(), false);
    this.controller = controller;

    final var config = controller.getConfiguration();
    OnUpdateFilter internalOnUpdateFilter =
        onUpdateFinalizerNeededAndApplied(controller.useFinalizer(),
            config.getFinalizerName())
            .or(onUpdateGenerationAware(config.isGenerationAware()))
            .or(onUpdateMarkedForDeletion());

    // by default the on add should be processed in all cases regarding internal filters
    config.onAddFilter().ifPresent(this::setOnAddFilter);
    config.onUpdateFilter()
        .ifPresentOrElse(filter -> setOnUpdateFilter(filter.and(internalOnUpdateFilter)),
            () -> setOnUpdateFilter(internalOnUpdateFilter));
    config.genericFilter().ifPresent(this::setGenericFilter);

    setConfigurationService(config.getConfigurationService());
  }

  @Override
  public synchronized void start() {
    try {
      super.start();
    } catch (KubernetesClientException e) {
      handleKubernetesClientException(e, controller.getConfiguration().getResourceTypeName());
      throw e;
    }
  }

  public void eventReceived(ResourceAction action, T resource, T oldResource) {
    try {
      if (log.isDebugEnabled()) {
        log.debug("Event received for resource: {} version: {} uuid: {} action: {}",
            ResourceID.fromResource(resource),
            getVersion(resource), resource.getMetadata().getUid(), action);
        log.trace("Event Old resource: {},\n new resource: {}", oldResource, resource);
      }
      MDCUtils.addResourceInfo(resource);
      controller.getEventSourceManager().broadcastOnResourceEvent(action, resource, oldResource);
      if (isAcceptedByFilters(action, resource, oldResource)) {
        getEventHandler().handleEvent(
            new ResourceEvent(action, ResourceID.fromResource(resource), resource));
      } else {
        log.debug("Skipping event handling resource {}",
            ResourceID.fromResource(resource));
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  private boolean isAcceptedByFilters(ResourceAction action, T resource, T oldResource) {
    // delete event is filtered for generic filter only.
    if (genericFilter != null && !genericFilter.accept(resource)) {
      return false;
    }
    switch (action) {
      case ADDED:
        return onAddFilter == null || onAddFilter.accept(resource);
      case UPDATED:
        return onUpdateFilter.accept(resource, oldResource);
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
  public void setOnDeleteFilter(OnDeleteFilter<? super T> onDeleteFilter) {
    throw new IllegalStateException(
        "onDeleteFilter is not supported for controller resource event source");
  }

  @Override
  public String name() {
    return NAME;
  }
}
