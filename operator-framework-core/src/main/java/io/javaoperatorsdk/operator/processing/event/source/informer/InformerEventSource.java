package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationEventFilter;
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
 * so after reconcile if getResource() called always return the fresh resource. To achieve this
 * handleRecentResourceUpdate() and handleRecentResourceCreate() needs to be called explicitly after
 * resource created/updated using the kubernetes client. (These calls are done automatically by
 * KubernetesDependentResource implementation.). In the background this will store the new resource
 * in a temporary cache {@link TemporaryResourceCache} which do additional checks. After a new event
 * is received the cachec object is removed from this cache, since in general then it is already in
 * the cache of informer.
 * </p>
 * <br>
 * <p>
 * 2. Additional API is provided that is ment to be used with the combination of the previous one,
 * and the goal is to filter out events that are the results of updates and creates made by the
 * controller itself. For example if in reconciler a ConfigMaps is created, there should be an
 * Informer in place to handle change events of that ConfigMap, but since it has bean created (or
 * updated) by the reconciler this should not trigger an additional reconciliation by default. In
 * order to achieve this prepareForCreateOrUpdateEventFiltering(..) method needs to be called before
 * the operation of the k8s client. And the operation from point 1. after the k8s client call. See
 * it's usage in CreateUpdateEventFilterTestReconciler integration test for the usage. (Again this
 * is managed for the developer if using dependent resources.) <br>
 * Roughly it works in a way that before the K8S API call is made, we set mark the resource ID, and
 * from that point informer won't propagate events further just will start record them. After the
 * client operation is done, it's checked and analysed what events were received and based on that
 * it will propagate event or not and/or put the new resource into the temporal cache - so if the
 * event not arrived yet about the update will be able to filter it in the future.
 * </p>
 *
 * @param <R> resource type watching
 * @param <P> type of the primary resource
 */
public class InformerEventSource<R extends HasMetadata, P extends HasMetadata>
    extends ManagedInformerEventSource<R, P, InformerConfiguration<R, P>>
    implements ResourceCache<R>, ResourceEventHandler<R>, RecentOperationEventFilter<R> {

  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);

  private final InformerConfiguration<R, P> configuration;
  // always called from a synchronized method
  private final EventRecorder<R> eventRecorder = new EventRecorder<>();

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

  private synchronized void onAddOrUpdate(String operation, R newObject, Runnable superOnOp) {
    var resourceID = ResourceID.fromResource(newObject);
    if (eventRecorder.isRecordingFor(resourceID)) {
      log.debug("Recording event for: {}", resourceID);
      eventRecorder.recordEvent(newObject);
      return;
    }
    if (temporalCacheHasResourceWithVersionAs(newObject)) {
      log.debug(
          "Skipping event propagation for {}, since was a result of a reconcile action. Resource ID: {}",
          operation,
          ResourceID.fromResource(newObject));
      superOnOp.run();
    } else {
      superOnOp.run();
      log.debug(
          "Propagating event for {}, resource with same version not result of a reconciliation. Resource ID: {}",
          operation,
          resourceID);
      propagateEvent(newObject);
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
  public synchronized void handleRecentResourceUpdate(ResourceID resourceID, R resource,
      R previousResourceVersion) {
    handleRecentCreateOrUpdate(resource,
        () -> super.handleRecentResourceUpdate(resourceID, resource,
            previousResourceVersion));
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    handleRecentCreateOrUpdate(resource,
        () -> super.handleRecentResourceCreate(resourceID, resource));
  }

  private void handleRecentCreateOrUpdate(R resource, Runnable runnable) {
    if (eventRecorder.isRecordingFor(ResourceID.fromResource(resource))) {
      handleRecentResourceOperationAndStopEventRecording(resource);
    } else {
      runnable.run();
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
    ResourceID resourceID = ResourceID.fromResource(resource);
    try {
      if (!eventRecorder.containsEventWithResourceVersion(
          resourceID, resource.getMetadata().getResourceVersion())) {
        log.debug(
            "Did not found event in buffer with target version and resource id: {}", resourceID);
        temporaryResourceCache.unconditionallyCacheResource(resource);
      } else if (eventRecorder.containsEventWithVersionButItsNotLastOne(
          resourceID, resource.getMetadata().getResourceVersion())) {
        R lastEvent = eventRecorder.getLastEvent(resourceID);
        log.debug(
            "Found events in event buffer but the target event is not last for id: {}. Propagating event.",
            resourceID);
        propagateEvent(lastEvent);
      }
    } finally {
      eventRecorder.stopEventRecording(resourceID);
    }
  }

  @Override
  public synchronized void prepareForCreateOrUpdateEventFiltering(ResourceID resourceID,
      R resource) {
    log.debug("Starting event recording for: {}", resourceID);
    eventRecorder.startEventRecording(resourceID);
  }

  /**
   * Mean to be called to clean up in case of an exception from the client. Usually in a catch
   * block.
   *
   * @param resource handled by the informer
   */
  @Override
  public synchronized void cleanupOnCreateOrUpdateEventFiltering(ResourceID resourceID,
      R resource) {
    log.debug("Stopping event recording for: {}", resourceID);
    eventRecorder.stopEventRecording(resourceID);
  }

}
