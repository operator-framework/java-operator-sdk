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

/**
 * <p>
 * Wraps informer(s) so it is connected to the eventing system of the framework. Note that since
 * it's it is built on top of Informers, it also support caching resources using caching from
 * fabric8 client Informer caches and additional caches described below.
 * </p>
 * <p>
 * InformerEventSource also supports two features to better handle events and caching of resources
 * on top of Informers from fabric8 Kubernetes client. These two features implementation wise are
 * related to each other:
 * </p>
 * <br>
 * <p>
 * 1. API that allows to make sure the cache contains the fresh resource after an update. This is
 * important for {@link io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} and
 * mainly for
 * {@link io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource}
 * so after reconcile if getResource() called always return the fresh resource. For that
 * handleRecentResourceUpdate() and handleRecentResourceCreate() needs to be called explicitly after
 * resource created/updated using the kubernetes client. (These calls are done automatically by
 * KubernetesDependentResource implementation.) todo how it works
 * </p>
 * <br>
 * <p>
 * 2. todo
 * </p>
 *
 * @param <R> resource type watching
 * @param <P> type of the primary resource
 */
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
    onAddOrUpdate("add", resource, () -> InformerEventSource.super.onAdd(resource));
  }

  @Override
  public void onUpdate(R oldObject, R newObject) {
    onAddOrUpdate("update", newObject,
        () -> InformerEventSource.super.onUpdate(oldObject, newObject));
  }

  private void onAddOrUpdate(String operation, R newObject, Runnable superOnOp) {
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
            "Skipping event propagation for {}, resource with same version found in temporal cache: {}",
            operation,
            ResourceID.fromResource(newObject));
        superOnOp.run();
      } else {
        superOnOp.run();
        log.debug(
            "Propagating event for {}, resource with same version not found in temporal cache: {}",
            operation,
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

  @Override
  public void handleRecentResourceUpdate(R resource, String previousResourceVersion) {
    handleRecentCreateOrUpdate(resource,
        () -> super.handleRecentResourceUpdate(resource, previousResourceVersion));
  }

  @Override
  public void handleRecentResourceCreate(R resource) {
    handleRecentCreateOrUpdate(resource, () -> super.handleRecentResourceCreate(resource));
  }

  private void handleRecentCreateOrUpdate(R resource, Runnable runnable) {
    lock.lock();
    try {
      if (eventBuffer.isEventsRecordedFor(ResourceID.fromResource(resource))) {
        handleRecentResourceOperationAndStopEventRecording(resource);
      } else {
        runnable.run();
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
  private void handleRecentResourceOperationAndStopEventRecording(R resource) {
    lock.lock();
    ResourceID resourceID = ResourceID.fromResource(resource);
    try {
      if (!eventBuffer.containsEventWithResourceVersion(
          resourceID, resource.getMetadata().getResourceVersion())) {
        log.debug(
            "Did not found event in buffer with target version and resource id: {}", resourceID);
        temporaryResourceCache.unconditionallyCacheResource(resource);
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

  public void prepareForCreateOrUpdateEventFiltering(ResourceID resourceID) {
    lock.lock();
    try {
      log.info("Starting event recording for: {}", resourceID);
      eventBuffer.startEventRecording(resourceID);
    } finally {
      lock.unlock();
    }
  }

  public void cleanupOnCreateOrUpdateEventFiltering(ResourceID resourceID) {
    lock.lock();
    try {
      log.info("Stopping event recording for: {}", resourceID);
      eventBuffer.stopEventRecording(resourceID);
    } finally {
      lock.unlock();
    }
  }
}
