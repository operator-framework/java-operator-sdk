package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationEventFilter;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;

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
 * 2. Additional API is provided that is meant to be used with the combination of the previous one,
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
    extends ManagedInformerEventSource<R, P, InformerConfiguration<R>>
    implements ResourceEventHandler<R>, RecentOperationEventFilter<R> {

  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);

  private final InformerConfiguration<R> configuration;
  // always called from a synchronized method
  private final EventRecorder<R> eventRecorder = new EventRecorder<>();
  // we need direct control for the indexer to propagate the just update resource also to the index
  private final PrimaryToSecondaryIndex<R> primaryToSecondaryIndex;
  private final PrimaryToSecondaryMapper<P> primaryToSecondaryMapper;
  private Map<String, Function<R, List<String>>> indexerBuffer = new HashMap<>();

  public InformerEventSource(
      InformerConfiguration<R> configuration, EventSourceContext<P> context) {
    this(configuration, context.getClient());
  }

  public InformerEventSource(InformerConfiguration<R> configuration, KubernetesClient client) {
    super(client.resources(configuration.getResourceClass()), configuration);
    this.configuration = configuration;


    // If there is a primary to secondary mapper there is no need for primary to secondary index.
    primaryToSecondaryMapper = configuration.getPrimaryToSecondaryMapper();
    if (primaryToSecondaryMapper == null) {
      primaryToSecondaryIndex =
          // The index uses the secondary to primary mapper (always present) to build the index
          new DefaultPrimaryToSecondaryIndex<>(configuration.getSecondaryToPrimaryMapper());
    } else {
      primaryToSecondaryIndex = NOOPPrimaryToSecondaryIndex.getInstance();
    }

    onAddFilter = configuration.onAddFilter().orElse(null);
    onUpdateFilter = configuration.onUpdateFilter().orElse(null);
    onDeleteFilter = configuration.onDeleteFilter().orElse(null);
    genericFilter = configuration.genericFilter().orElse(null);
  }

  @Override
  public void onAdd(R newResource) {
    if (log.isDebugEnabled()) {
      log.debug("On add event received for resource id: {} type: {} version: {}",
          ResourceID.fromResource(newResource),
          resourceType().getSimpleName(), newResource.getMetadata().getResourceVersion());
    }
    primaryToSecondaryIndex.onAddOrUpdate(newResource);
    onAddOrUpdate(Operation.ADD, newResource, null,
        () -> InformerEventSource.super.onAdd(newResource));
  }

  @Override
  public void onUpdate(R oldObject, R newObject) {
    if (log.isDebugEnabled()) {
      log.debug(
          "On update event received for resource id: {} type: {} version: {} old version: {} ",
          ResourceID.fromResource(newObject),
          resourceType().getSimpleName(),
          newObject.getMetadata().getResourceVersion(),
          oldObject.getMetadata().getResourceVersion());
    }
    primaryToSecondaryIndex.onAddOrUpdate(newObject);
    onAddOrUpdate(Operation.UPDATE, newObject, oldObject,
        () -> InformerEventSource.super.onUpdate(oldObject, newObject));
  }

  @Override
  public void onDelete(R resource, boolean b) {
    if (log.isDebugEnabled()) {
      log.debug("On delete event received for resource id: {} type: {}",
          ResourceID.fromResource(resource),
          resourceType().getSimpleName());
    }
    primaryToSecondaryIndex.onDelete(resource);
    super.onDelete(resource, b);
    if (acceptedByDeleteFilters(resource, b)) {
      propagateEvent(resource);
    }
  }

  private synchronized void onAddOrUpdate(Operation operation, R newObject, R oldObject,
      Runnable superOnOp) {
    var resourceID = ResourceID.fromResource(newObject);
    if (eventRecorder.isRecordingFor(resourceID)) {
      log.debug("Recording event for: {}", resourceID);
      eventRecorder.recordEvent(newObject);
      return;
    }
    if (temporaryCacheHasResourceWithSameVersionAs(newObject)) {
      log.debug(
          "Skipping event propagation for {}, since was a result of a reconcile action. Resource ID: {}",
          operation,
          ResourceID.fromResource(newObject));
      superOnOp.run();
    } else {
      superOnOp.run();
      if (eventAcceptedByFilter(operation, newObject, oldObject)) {
        log.debug(
            "Propagating event for {}, resource with same version not result of a reconciliation. Resource ID: {}",
            operation,
            resourceID);
        propagateEvent(newObject);
      } else {
        log.debug("Event filtered out for operation: {}, resourceID: {}", operation, resourceID);
      }
    }
  }

  private boolean temporaryCacheHasResourceWithSameVersionAs(R resource) {
    var resourceID = ResourceID.fromResource(resource);
    var res = temporaryResourceCache.getResourceFromCache(resourceID);
    return res.map(r -> {
      boolean resVersionsEqual = r.getMetadata().getResourceVersion()
          .equals(resource.getMetadata().getResourceVersion());
      log.debug("Resource found in temporal cache for id: {} resource versions equal: {}",
          resourceID, resVersionsEqual);
      return resVersionsEqual;
    }).orElse(false);
  }

  private void propagateEvent(R object) {
    var primaryResourceIdSet =
        configuration.getSecondaryToPrimaryMapper().toPrimaryResourceIDs(object);
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
  public Set<R> getSecondaryResources(P primary) {
    Set<ResourceID> secondaryIDs;
    if (useSecondaryToPrimaryIndex()) {
      var primaryResourceID = ResourceID.fromResource(primary);
      secondaryIDs =
          primaryToSecondaryIndex.getSecondaryResources(primaryResourceID);
      log.debug(
          "Using PrimaryToSecondaryIndex to find secondary resources for primary: {}. Found secondary ids: {} ",
          primaryResourceID, secondaryIDs);
    } else {
      secondaryIDs = primaryToSecondaryMapper.toSecondaryResourceIDs(primary);
      log.debug(
          "Using PrimaryToSecondaryMapper to find secondary resources for primary: {}. Found secondary ids: {} ",
          primary, secondaryIDs);
    }
    return secondaryIDs.stream().map(this::get).flatMap(Optional::stream)
        .collect(Collectors.toSet());
  }

  public InformerConfiguration<R> getConfiguration() {
    return configuration;
  }

  @Override
  public synchronized void handleRecentResourceUpdate(ResourceID resourceID, R resource,
      R previousVersionOfResource) {
    handleRecentCreateOrUpdate(Operation.UPDATE, resource, previousVersionOfResource,
        () -> super.handleRecentResourceUpdate(resourceID, resource, previousVersionOfResource));
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    handleRecentCreateOrUpdate(Operation.ADD, resource, null,
        () -> super.handleRecentResourceCreate(resourceID, resource));
  }

  private void handleRecentCreateOrUpdate(Operation operation, R resource, R oldResource,
      Runnable runnable) {
    primaryToSecondaryIndex.onAddOrUpdate(resource);
    if (eventRecorder.isRecordingFor(ResourceID.fromResource(resource))) {
      handleRecentResourceOperationAndStopEventRecording(operation, resource, oldResource);
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
   * @param newResource just created or updated resource
   */
  private void handleRecentResourceOperationAndStopEventRecording(Operation operation,
      R newResource, R oldResource) {
    ResourceID resourceID = ResourceID.fromResource(newResource);
    try {
      if (!eventRecorder.containsEventWithResourceVersion(
          resourceID, newResource.getMetadata().getResourceVersion())) {
        log.debug(
            "Did not found event in buffer with target version and resource id: {}", resourceID);
        temporaryResourceCache.unconditionallyCacheResource(newResource);
      } else {
        // if the resource is not added to the temp cache, it is cleared, since
        // the cache is cleared by subsequent events after updates, but if those did not receive
        // the temp cache is still filled at this point with an old resource
        log.debug("Cleaning temporary cache for resource id: {}", resourceID);
        temporaryResourceCache.removeResourceFromCache(newResource);
        if (eventRecorder.containsEventWithVersionButItsNotLastOne(
            resourceID, newResource.getMetadata().getResourceVersion())) {
          R lastEvent = eventRecorder.getLastEvent(resourceID);

          log.debug(
              "Found events in event buffer but the target event is not last for id: {}. Propagating event.",
              resourceID);
          if (eventAcceptedByFilter(operation, newResource, oldResource)) {
            propagateEvent(lastEvent);
          }
        }
      }
    } finally {
      log.debug("Stopping event recording for: {}", resourceID);
      eventRecorder.stopEventRecording(resourceID);
    }
  }

  private boolean useSecondaryToPrimaryIndex() {
    return this.primaryToSecondaryMapper == null;
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
   * @param resourceID to cleanup
   */
  @Override
  public synchronized void cleanupOnCreateOrUpdateEventFiltering(ResourceID resourceID) {
    log.debug("Stopping event recording for: {}", resourceID);
    eventRecorder.stopEventRecording(resourceID);
  }

  @Override
  public boolean allowsNamespaceChanges() {
    return getConfiguration().followControllerNamespaceChanges();
  }


  private boolean eventAcceptedByFilter(Operation operation, R newObject, R oldObject) {
    if (genericFilter != null && !genericFilter.accept(newObject)) {
      return false;
    }
    if (operation == Operation.ADD) {
      return onAddFilter == null || onAddFilter.accept(newObject);
    } else {
      return onUpdateFilter == null || onUpdateFilter.accept(newObject, oldObject);
    }
  }

  private enum Operation {
    ADD, UPDATE
  }

  private boolean acceptedByDeleteFilters(R resource, boolean b) {
    return (onDeleteFilter == null || onDeleteFilter.accept(resource, b)) &&
        (genericFilter == null || genericFilter.accept(resource));
  }


  // Since this event source instance is created by the user, the ConfigurationService is actually
  // injected after it is registered. Some of the subcomponents are initialized at that time here.
  public void setConfigurationService(ConfigurationService configurationService) {
    super.setConfigurationService(configurationService);

    cache.addIndexers(indexerBuffer);
    indexerBuffer = null;
  }

  public void addIndexers(Map<String, Function<R, List<String>>> indexers) {
    if (indexerBuffer == null) {
      throw new OperatorException("Cannot add indexers after InformerEventSource started.");
    }
    indexerBuffer.putAll(indexers);
  }

}
