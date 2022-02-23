package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

public class InformerEventSource<R extends HasMetadata, P extends HasMetadata>
    extends ManagedInformerEventSource<R, P, InformerConfiguration<R, P>>
    implements ResourceCache<R>, ResourceEventHandler<R> {

  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);

  private final InformerConfiguration<R, P> configuration;
  private final EventBuffer<R> eventBuffer = new EventBuffer<>();
  private final ReentrantLock lock = new ReentrantLock();

  public InformerEventSource(
      InformerConfiguration<R, P> configuration, EventSourceContext<P> context) {
    super(context.getClient().resources(configuration.getResourceClass()), configuration);
    this.configuration = configuration;
  }

  public InformerEventSource(InformerConfiguration<R, P> configuration, KubernetesClient client) {
    super(client.resources(configuration.getResourceClass()), configuration);
    this.configuration = configuration;
  }

  @Override
  public void onAdd(R resource) {
    lock.lock();
    try {
      if (temporalCacheHasResourceWithVersionAs(resource)) {
        super.onAdd(resource);
        if (log.isDebugEnabled()) {
          log.debug(
              "Skipping event propagation for Add, resource with same version found in temporal cache: {}",
              ResourceID.fromResource(resource));
        }
      } else {
        var resourceID = ResourceID.fromResource(resource);
        if (eventBuffer.isEventsRecordedFor(resourceID)) {
          eventBuffer.eventReceived(resource);
        } else {
          super.onAdd(resource);
          if (log.isDebugEnabled()) {
            log.debug(
                "Propagating event for add, resource with same version not found in temporal cache: {}",
                resourceID);
          }
          propagateEvent(resource);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void onUpdate(R oldObject, R newObject) {
    lock.lock();
    try {
      if (temporalCacheHasResourceWithVersionAs(newObject)) {
        log.debug(
            "Skipping event propagation for Update, resource with same version found in temporal cache: {}",
            ResourceID.fromResource(newObject));
        super.onUpdate(oldObject, newObject);
      } else {
        var resourceID = ResourceID.fromResource(newObject);
        if (eventBuffer.isEventsRecordedFor(resourceID)) {
          eventBuffer.eventReceived(newObject);
        } else {
          super.onUpdate(oldObject, newObject);
          if (oldObject
              .getMetadata()
              .getResourceVersion()
              .equals(newObject.getMetadata().getResourceVersion())) {
            return;
          }
          log.debug(
              "Propagating event for update, resource with same version not found in temporal cache: {}",
              ResourceID.fromResource(newObject));
          propagateEvent(newObject);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void onDelete(R r, boolean b) {
    super.onDelete(r, b);
    propagateEvent(r);
  }

  private void propagateEvent(R object) {
    var primaryResourceIdSet =
        configuration.getPrimaryResourcesRetriever().associatedPrimaryResources(object);
    if (primaryResourceIdSet.isEmpty()) {
      return;
    }
    primaryResourceIdSet.forEach(
        resourceId -> {
          Event event = new Event(resourceId);
          /*
           * In fabric8 client for certain cases informers can be created on in a way that they are
           * automatically started, what would cause a NullPointerException here, since an event
           * might be received between creation and registration.
           */
          final EventHandler eventHandler = getEventHandler();
          if (eventHandler != null) {
            eventHandler.handleEvent(event);
          }
        });
  }

  @Override
  public void start() {
    manager().start();
  }

  @Override
  public void stop() {
    manager().stop();
  }

  /**
   * Retrieves the informed resource associated with the specified primary resource as defined by
   * the function provided when this InformerEventSource was created
   *
   * @param resource the primary resource we want to retrieve the associated resource for
   * @return the informed resource associated with the specified primary resource
   */
  @Override
  public Optional<R> getAssociated(P resource) {
    final var id = configuration.getAssociatedResourceIdentifier().associatedSecondaryID(resource);
    return get(id);
  }

  public InformerConfiguration<R, P> getConfiguration() {
    return configuration;
  }

  public void willCreateOrUpdateForResource(ResourceID resourceID) {
    eventBuffer.startEventRecording(resourceID);
  }

  public void handleJustUpdatedResource(R resource, String previousResourceVersion) {
    lock.lock();
    ResourceID resourceID = ResourceID.fromResource(resource);
    try {
      if (!eventBuffer.containsEventWithResourceVersion(
          resourceID, resource.getMetadata().getResourceVersion())) {
        temporalResourceCache.putUpdatedResource(resource, previousResourceVersion);
      } else if (!eventBuffer.containsEventWithVersionButItsNotLastOne(
          resourceID, resource.getMetadata().getResourceVersion())) {
        R lastEvent = eventBuffer.getLastEvent(resourceID);
        propagateEvent(lastEvent);
      }
    } finally {
      eventBuffer.stopEventRecording(resourceID);
      lock.unlock();
    }
  }

  public void handleJustAddedResource(R resource) {
    lock.lock();
    ResourceID resourceID = ResourceID.fromResource(resource);
    try {
      if (!eventBuffer.containsEventWithResourceVersion(
          resourceID, resource.getMetadata().getResourceVersion())) {
        temporalResourceCache.putAddedResource(resource);
      } else if (!eventBuffer.containsEventWithVersionButItsNotLastOne(
          resourceID, resource.getMetadata().getResourceVersion())) {
        R lastEvent = eventBuffer.getLastEvent(resourceID);
        propagateEvent(lastEvent);
      }
    } finally {
      eventBuffer.stopEventRecording(resourceID);
      lock.unlock();
    }
  }

  public void cleanupOnUpdateAndCreate(R resource) {
    lock.lock();
    try {
      eventBuffer.stopEventRecording(ResourceID.fromResource(resource));
    } finally {
      lock.unlock();
    }
  }
}
