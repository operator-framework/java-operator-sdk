package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public class ControllerResourceEventSource<T extends HasMetadata>
    extends ManagedInformerEventSource<T, T, ControllerConfiguration<T>>
    implements ResourceEventHandler<T> {

  public static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";
  private static final Logger log = LoggerFactory.getLogger(ControllerResourceEventSource.class);

  private final Controller<T> controller;
  private final ResourceEventFilter<T> filter;

  public ControllerResourceEventSource(Controller<T> controller) {
    super(controller.getCRClient(), controller.getConfiguration());
    this.controller = controller;
    var filters = new ResourceEventFilter[] {
        ResourceEventFilters.finalizerNeededAndApplied(),
        ResourceEventFilters.markedForDeletion(),
        ResourceEventFilters.generationAware(),
    };
    if (controller.getConfiguration().getEventFilter() != null) {
      filter = controller.getConfiguration().getEventFilter().and(ResourceEventFilters.or(filters));
    } else {
      filter = ResourceEventFilters.or(filters);
    }
  }

  @Override
  public void start() {
    try {
      super.start();
    } catch (KubernetesClientException e) {
      handleKubernetesClientException(e);
      throw e;
    }
  }

  public void eventReceived(ResourceAction action, T resource, T oldResource) {
    try {
      log.debug("Event received for resource: {}", getName(resource));
      MDCUtils.addResourceInfo(resource);
      controller.getEventSourceManager().broadcastOnResourceEvent(action, resource, oldResource);
      if (filter.acceptChange(controller.getConfiguration(), oldResource, resource)) {
        getEventHandler().handleEvent(
            new ResourceEvent(action, ResourceID.fromResource(resource)));
      } else {
        log.debug("Skipping event handling resource {} with version: {}", getUID(resource),
            getVersion(resource));
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
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
    return get(ResourceID.fromResource(primary));
  }
}
