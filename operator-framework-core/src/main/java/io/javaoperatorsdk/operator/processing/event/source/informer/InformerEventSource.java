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
      var resourceID = ResourceID.fromResource(resource);
      if (eventBuffer.isEventsRecordedFor(resourceID)) {
        eventBuffer.eventReceived(resource);
        return;
      }
      if (temporalCacheHasResourceWithVersionAs(resource)) {
        super.onAdd(resource);
        log.debug(
            "Skipping event propagation for Add, resource with same version found in temporal cache: {}",
            resourceID);
      } else {
        super.onAdd(resource);

        log.debug(
            "Propagating event for add, resource with same version not found in temporal cache: {}",
            resourceID);
        propagateEvent(resource);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void onUpdate(R oldObject, R newObject) {
    lock.lock();
    try {
      var resourceID = ResourceID.fromResource(newObject);
      if (eventBuffer.isEventsRecordedFor(resourceID)) {
        log.info("Recording event for: " + resourceID);
        eventBuffer.eventReceived(newObject);
        return;
      }
      if (temporalCacheHasResourceWithVersionAs(newObject)) {
        log.debug(
            "Skipping event propagation for Update, resource with same version found in temporal cache: {}",
            ResourceID.fromResource(newObject));
        super.onUpdate(oldObject, newObject);
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
            resourceID);
        propagateEvent(newObject);
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
    lock.lock();
    try {
      log.info("Starting event recording for: {}", resourceID);
      eventBuffer.startEventRecording(resourceID);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void handleRecentResourceUpdate(R resource, String previousResourceVersion) {
    lock.lock();
    try {
      if (eventBuffer.isEventsRecordedFor(ResourceID.fromResource(resource))) {
        handleRecentResourceOperation(resource);
      } else {
        super.handleRecentResourceUpdate(resource, previousResourceVersion);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void handleRecentResourceAdd(R resource) {
    lock.lock();
    try {
      if (eventBuffer.isEventsRecordedFor(ResourceID.fromResource(resource))) {
        handleRecentResourceOperation(resource);
      } else {
        super.handleRecentResourceAdd(resource);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * There can be the following cases:
   * <ul>
   * <li>1. Did not receive the event yet for the target resource, then we need to put it to temp
   * cache. Because event will arrive. Note that this not necessary mean that the even is not sent
   * yet (we are in sync context). Also does not mean that there are no more events received after
   * that. But during the event processing (onAdd, onUpdate) we make sure that the propagation just
   * skipped for the right event.</li>
   * <li>2. Received the event about the operation already, it was the last. This means already is
   * on cache of informer. So we have to do nothing. Since it was just recorded and not propagated.
   * </li>
   * <li>3. Received the event but more events received since, so those were not propagated yet. So
   * an event needs to be propagated to compensate.</li>
   * </ul>
   * 
   * @param resource just created or updated resource
   */
  private void handleRecentResourceOperation(R resource) {
    lock.lock();
    ResourceID resourceID = ResourceID.fromResource(resource);
    try {
      if (!eventBuffer.containsEventWithResourceVersion(
          resourceID, resource.getMetadata().getResourceVersion())) {
        log.debug(
            "Did not found event in buffer with target version and resource id: {}", resourceID);
        temporalResourceCache.unconditionallyCacheResource(resource);
      } else if (eventBuffer.containsEventWithVersionButItsNotLastOne(
          resourceID, resource.getMetadata().getResourceVersion())) {
        R lastEvent = eventBuffer.getLastEvent(resourceID);
        log.debug(
            "Found events in event buffer but the target event is not last for id: {}. Propagating event.",
            resourceID);
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
      var resourceID = ResourceID.fromResource(resource);
      log.info("Stopping event recording for: {}", resourceID);
      eventBuffer.stopEventRecording(resourceID);
    } finally {
      lock.unlock();
    }
  }
}
