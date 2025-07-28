package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;

/**
 * Wraps informer(s) so they are connected to the eventing system of the framework. Note that since
 * this is built on top of Fabric8 client Informers, it also supports caching resources using
 * caching from informer caches as well as additional caches described below.
 *
 * <p>InformerEventSource also supports two features to better handle events and caching of
 * resources on top of Informers from the Fabric8 Kubernetes client. These two features are related
 * to each other as follows:
 *
 * <ol>
 *   <li>Ensuring the cache contains the fresh resource after an update. This is important for
 *       {@link io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} and mainly
 *       for {@link
 *       io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource} so
 *       that {@link
 *       io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource#getSecondaryResource(HasMetadata,
 *       Context)} always returns the latest version of the resource after a reconciliation. To
 *       achieve this {@link #handleRecentResourceUpdate(ResourceID, HasMetadata, HasMetadata)} and
 *       {@link #handleRecentResourceCreate(ResourceID, HasMetadata)} need to be called explicitly
 *       after a resource is created or updated using the kubernetes client. These calls are done
 *       automatically by the KubernetesDependentResource implementation. In the background this
 *       will store the new resource in a temporary cache {@link TemporaryResourceCache} which does
 *       additional checks. After a new event is received the cached object is removed from this
 *       cache, since it is then usually already in the informer cache.
 *   <li>Avoiding unneeded reconciliations after resources are created or updated. This filters out
 *       events that are the results of updates and creates made by the controller itself because we
 *       typically don't want the associated informer to trigger an event causing a useless
 *       reconciliation (as the change originates from the reconciler itself). For the details see
 *       {@link #canSkipEvent(HasMetadata, HasMetadata, ResourceID)} and related usage.
 * </ol>
 *
 * @param <R> resource type being watched
 * @param <P> type of the associated primary resource
 */
public class InformerEventSource<R extends HasMetadata, P extends HasMetadata>
    extends ManagedInformerEventSource<R, P, InformerEventSourceConfiguration<R>>
    implements ResourceEventHandler<R> {

  public static final String PRIMARY_TO_SECONDARY_INDEX_NAME = "primaryToSecondary";

  public static final String PREVIOUS_ANNOTATION_KEY = "javaoperatorsdk.io/previous";
  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);
  // we need direct control for the indexer to propagate the just update resource also to the index
  private final PrimaryToSecondaryMapper<P> primaryToSecondaryMapper;
  private final String id = UUID.randomUUID().toString();

  public InformerEventSource(
      InformerEventSourceConfiguration<R> configuration, EventSourceContext<P> context) {
    this(
        configuration,
        configuration.getKubernetesClient().orElse(context.getClient()),
        context
            .getControllerConfiguration()
            .getConfigurationService()
            .parseResourceVersionsForEventFilteringAndCaching());
  }

  InformerEventSource(InformerEventSourceConfiguration<R> configuration, KubernetesClient client) {
    this(configuration, client, false);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private InformerEventSource(
      InformerEventSourceConfiguration<R> configuration,
      KubernetesClient client,
      boolean parseResourceVersions) {
    super(
        configuration.name(),
        configuration
            .getGroupVersionKind()
            .map(gvk -> client.genericKubernetesResources(gvk.apiVersion(), gvk.getKind()))
            .orElseGet(() -> (MixedOperation) client.resources(configuration.getResourceClass())),
        configuration,
        parseResourceVersions);
    // If there is a primary to secondary mapper there is no need for primary to secondary index.
    primaryToSecondaryMapper = configuration.getPrimaryToSecondaryMapper();
    if (useSecondaryToPrimaryIndex()) {
      addIndexers(
          Map.of(
              PRIMARY_TO_SECONDARY_INDEX_NAME,
              (R r) ->
                  configuration.getSecondaryToPrimaryMapper().toPrimaryResourceIDs(r).stream()
                      .map(InformerEventSource::resourceIdToString)
                      .toList()));
    }

    final var informerConfig = configuration.getInformerConfig();
    onAddFilter = informerConfig.getOnAddFilter();
    onUpdateFilter = informerConfig.getOnUpdateFilter();
    onDeleteFilter = informerConfig.getOnDeleteFilter();
    genericFilter = informerConfig.getGenericFilter();
  }

  @Override
  public void onAdd(R newResource) {
    if (log.isDebugEnabled()) {
      log.debug(
          "On add event received for resource id: {} type: {} version: {}",
          ResourceID.fromResource(newResource),
          resourceType().getSimpleName(),
          newResource.getMetadata().getResourceVersion());
    }
    onAddOrUpdate(
        Operation.ADD, newResource, null, () -> InformerEventSource.super.onAdd(newResource));
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

    onAddOrUpdate(
        Operation.UPDATE,
        newObject,
        oldObject,
        () -> InformerEventSource.super.onUpdate(oldObject, newObject));
  }

  @Override
  public void onDelete(R resource, boolean b) {
    if (log.isDebugEnabled()) {
      log.debug(
          "On delete event received for resource id: {} type: {}",
          ResourceID.fromResource(resource),
          resourceType().getSimpleName());
    }
    super.onDelete(resource, b);
    if (acceptedByDeleteFilters(resource, b)) {
      propagateEvent(resource);
    }
  }

  private synchronized void onAddOrUpdate(
      Operation operation, R newObject, R oldObject, Runnable superOnOp) {
    var resourceID = ResourceID.fromResource(newObject);
    if (canSkipEvent(newObject, oldObject, resourceID)) {
      log.debug(
          "Skipping event propagation for {}, since was a result of a reconcile action. Resource"
              + " ID: {}",
          operation,
          ResourceID.fromResource(newObject));
      superOnOp.run();
    } else {
      superOnOp.run();
      if (eventAcceptedByFilter(operation, newObject, oldObject)) {
        log.debug(
            "Propagating event for {}, resource with same version not result of a reconciliation."
                + " Resource ID: {}",
            operation,
            resourceID);
        propagateEvent(newObject);
      } else {
        log.debug("Event filtered out for operation: {}, resourceID: {}", operation, resourceID);
      }
    }
  }

  private boolean canSkipEvent(R newObject, R oldObject, ResourceID resourceID) {
    if (temporaryResourceCache.isKnownResourceVersion(newObject)) {
      return true;
    }
    var res = temporaryResourceCache.getResourceFromCache(resourceID);
    if (res.isEmpty()) {
      return isEventKnownFromAnnotation(newObject, oldObject);
    }
    boolean resVersionsEqual =
        newObject
            .getMetadata()
            .getResourceVersion()
            .equals(res.get().getMetadata().getResourceVersion());
    log.debug(
        "Resource found in temporal cache for id: {} resource versions equal: {}",
        resourceID,
        resVersionsEqual);
    return resVersionsEqual;
  }

  private boolean isEventKnownFromAnnotation(R newObject, R oldObject) {
    String previous = newObject.getMetadata().getAnnotations().get(PREVIOUS_ANNOTATION_KEY);
    boolean known = false;
    if (previous != null) {
      String[] parts = previous.split(",");
      if (id.equals(parts[0])) {
        if (oldObject == null && parts.length == 1) {
          known = true;
        } else if (oldObject != null
            && parts.length == 2
            && oldObject.getMetadata().getResourceVersion().equals(parts[1])) {
          known = true;
        }
      }
    }
    return known;
  }

  private void propagateEvent(R object) {
    var primaryResourceIdSet =
        configuration().getSecondaryToPrimaryMapper().toPrimaryResourceIDs(object);
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

    if (useSecondaryToPrimaryIndex()) {
      var primaryID = ResourceID.fromResource(primary);
      // Note that the order matter is these lines. This method is not synchronized
      // because of performance reasons. If it was in reverse order, it could happen
      // that we did not receive yet an event in the informer so the index would not
      // be updated. However, before reading it from temp IDs the event arrives and erases
      // the temp index. So in case of Add not id would be found.
      var temporalIds =
          temporaryResourceCache
              .getTemporalPrimaryToSecondaryIndex()
              .getSecondaryResources(primaryID);
      var resources = byIndex(PRIMARY_TO_SECONDARY_INDEX_NAME, resourceIdToString(primaryID));

      log.debug(
          "Using informer primary to secondary index to find secondary resources for primary name:"
              + " {} namespace: {}. Found number {}",
          primary.getMetadata().getName(),
          primary.getMetadata().getNamespace(),
          resources.size());

      log.debug("Complementary ids: {}", temporalIds);
      var res =
          resources.stream()
              .map(
                  r -> {
                    var resourceId = ResourceID.fromResource(r);
                    Optional<R> resource = temporaryResourceCache.getResourceFromCache(resourceId);
                    temporalIds.remove(resourceId);
                    return resource.orElse(r);
                  })
              .collect(Collectors.toSet());
      temporalIds.forEach(
          id -> {
            Optional<R> resource = get(id);
            resource.ifPresentOrElse(res::add, () -> log.warn("Resource not found: {}", id));
          });
      return res;
    } else {
      Set<ResourceID> secondaryIDs = primaryToSecondaryMapper.toSecondaryResourceIDs(primary);
      log.debug(
          "Using PrimaryToSecondaryMapper to find secondary resources for primary: {}. Found"
              + " secondary ids: {} ",
          primary,
          secondaryIDs);
      return secondaryIDs.stream()
          .map(this::get)
          .flatMap(Optional::stream)
          .collect(Collectors.toSet());
    }
  }

  @Override
  public synchronized void handleRecentResourceUpdate(
      ResourceID resourceID, R resource, R previousVersionOfResource) {
    handleRecentCreateOrUpdate(resource, previousVersionOfResource);
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    handleRecentCreateOrUpdate(resource, null);
  }

  private void handleRecentCreateOrUpdate(R newResource, R oldResource) {
    temporaryResourceCache.putResource(
        newResource,
        Optional.ofNullable(oldResource)
            .map(r -> r.getMetadata().getResourceVersion())
            .orElse(null));
  }

  private boolean useSecondaryToPrimaryIndex() {
    return this.primaryToSecondaryMapper == null;
  }

  @Override
  public boolean allowsNamespaceChanges() {
    return configuration().followControllerNamespaceChanges();
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

  private boolean acceptedByDeleteFilters(R resource, boolean b) {
    return (onDeleteFilter == null || onDeleteFilter.accept(resource, b))
        && (genericFilter == null || genericFilter.accept(resource));
  }

  /**
   * Add an annotation to the resource so that the subsequent will be omitted
   *
   * @param resourceVersion null if there is no prior version
   * @param target mutable resource that will be returned
   */
  public R addPreviousAnnotation(String resourceVersion, R target) {
    target
        .getMetadata()
        .getAnnotations()
        .put(
            PREVIOUS_ANNOTATION_KEY,
            id + Optional.ofNullable(resourceVersion).map(rv -> "," + rv).orElse(""));
    return target;
  }

  private enum Operation {
    ADD,
    UPDATE
  }

  private static String resourceIdToString(ResourceID resourceID) {
    return resourceID.getName() + "#" + resourceID.getNamespace().orElse("$na");
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected TemporaryResourceCache<R> temporaryResourceCache() {
    return new TemporaryResourceCache<>(
        this,
        useSecondaryToPrimaryIndex()
            ? new DefaultTemporalPrimaryToSecondaryIndex(
                configuration().getSecondaryToPrimaryMapper())
            : NOOPTemporalPrimaryToSecondaryIndex.getInstance(),
        parseResourceVersions);
  }
}
