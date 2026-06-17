/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

/**
 * Wraps informer(s) so they are connected to the eventing system of the framework. Note that since
 * this is built on top of Fabric8 client Informers, it also supports caching resources using
 * caching from informer caches as well as filtering events which are result of the controller's
 * update.
 *
 * @param <R> resource type being watched
 * @param <P> type of the associated primary resource
 */
public class InformerEventSource<R extends HasMetadata, P extends HasMetadata>
    extends ManagedInformerEventSource<R, P, InformerEventSourceConfiguration<R>>
    implements ResourceEventHandler<R> {

  public static final String PREVIOUS_ANNOTATION_KEY = "javaoperatorsdk.io/previous";
  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);
  // we need direct control for the indexer to propagate the just update resource also to the index
  private final PrimaryToSecondaryIndex<R> primaryToSecondaryIndex;
  private final PrimaryToSecondaryMapper<P> primaryToSecondaryMapper;

  public InformerEventSource(
      InformerEventSourceConfiguration<R> configuration, EventSourceContext<P> context) {
    this(configuration, configuration.getKubernetesClient().orElse(context.getClient()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  InformerEventSource(InformerEventSourceConfiguration<R> configuration, KubernetesClient client) {
    super(
        configuration.name(),
        configuration
            .getGroupVersionKind()
            .map(gvk -> client.genericKubernetesResources(gvk.apiVersion(), gvk.getKind()))
            .orElseGet(() -> (MixedOperation) client.resources(configuration.getResourceClass())),
        configuration);
    // If there is a primary to secondary mapper there is no need for primary to secondary index.
    primaryToSecondaryMapper = configuration.getPrimaryToSecondaryMapper();
    if (useSecondaryToPrimaryIndex()) {
      primaryToSecondaryIndex =
          // The index uses the secondary to primary mapper (always present) to build the index
          new DefaultPrimaryToSecondaryIndex<>(configuration.getSecondaryToPrimaryMapper());
    } else {
      primaryToSecondaryIndex = NOOPPrimaryToSecondaryIndex.getInstance();
    }

    final var informerConfig = configuration.getInformerConfig();
    onAddFilter = informerConfig.getOnAddFilter();
    onUpdateFilter = informerConfig.getOnUpdateFilter();
    onDeleteFilter = informerConfig.getOnDeleteFilter();
    genericFilter = informerConfig.getGenericFilter();
  }

  @Override
  public void onAdd(R newResource) {
    withMDC(
        newResource,
        ResourceAction.ADDED,
        () -> {
          if (log.isDebugEnabled()) {
            log.debug("On add event received");
          }
          onAddOrUpdate(ResourceAction.ADDED, newResource, null);
        });
  }

  @Override
  public void onUpdate(R oldObject, R newObject) {
    withMDC(
        newObject,
        ResourceAction.UPDATED,
        () -> {
          if (log.isDebugEnabled()) {
            log.debug(
                "On update event received. Old version: {}",
                oldObject.getMetadata().getResourceVersion());
          }
          onAddOrUpdate(ResourceAction.UPDATED, newObject, oldObject);
        });
  }

  @Override
  public synchronized void onDelete(R resource, boolean deletedFinalStateUnknown) {
    withMDC(
        resource,
        ResourceAction.DELETED,
        () -> {
          if (log.isDebugEnabled()) {
            log.debug(
                "On delete event received. deletedFinalStateUnknown: {}", deletedFinalStateUnknown);
          }
          var resultEvent =
              temporaryResourceCache.onDeleteEvent(resource, deletedFinalStateUnknown);
          if (resultEvent.isEmpty()) {
            return;
          }
          primaryToSecondaryIndex.onDelete(resource);
          if (eventAcceptedByFilter(
              ResourceAction.DELETED, resource, null, deletedFinalStateUnknown)) {
            propagateEvent(resource, null);
          }
        });
  }

  @Override
  protected void handleEvent(
      ResourceAction action, R resource, R oldResource, Boolean deletedFinalStateUnknown) {
    // Called from ManagedInformerEventSource#eventFilteringUpdateAndCacheResource after the temp
    // cache decided to surface a (possibly synthesized) event. The user-level filters
    // (onAdd/onUpdate/onDelete/genericFilter) still apply, so this path mirrors the direct
    // onAdd/onUpdate/onDelete watch paths. The index is updated for DELETED regardless of the
    // filter outcome — the resource is really gone, so leaving a tombstone in the index would
    // make getSecondaryResources keep returning a stale entry.
    if (action == ResourceAction.DELETED) {
      log.debug(
          "handleEvent: removing from primaryToSecondaryIndex. id={}",
          ResourceID.fromResource(resource));
      primaryToSecondaryIndex.onDelete(resource);
    }
    if (!eventAcceptedByFilter(action, resource, oldResource, deletedFinalStateUnknown)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "handleEvent: event rejected by user filter, not propagating. id={}, action={}",
            ResourceID.fromResource(resource),
            action);
      }
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "handleEvent: propagating event. id={}, action={}, rv={}",
          ResourceID.fromResource(resource),
          action,
          resource.getMetadata().getResourceVersion());
    }
    propagateEvent(resource, oldResource);
  }

  @Override
  public synchronized void start() {
    if (isRunning()) {
      return;
    }
    super.start();
    // this makes sure that on first reconciliation all resources are
    // present on the index
    manager().list().forEach(r -> primaryToSecondaryIndex.onAddOrUpdate(r, null));
  }

  @SuppressWarnings("unchecked")
  private synchronized void onAddOrUpdate(ResourceAction action, R newObject, R oldObject) {
    primaryToSecondaryIndex.onAddOrUpdate(newObject, oldObject);
    var resourceID = ResourceID.fromResource(newObject);

    var resultEvent = temporaryResourceCache.onAddOrUpdateEvent(action, newObject, oldObject);

    if (resultEvent.isEmpty()) {
      log.debug("Deferring event propagation");
    } else if (eventAcceptedByFilter(action, newObject, oldObject, null)) {
      log.debug(
          "Propagating event for {}, resource with same version not result of a our update.",
          action);
      var event = resultEvent.get();
      propagateEvent((R) event.getResource().orElseThrow(), oldObject);
    } else {
      log.debug("Event filtered out for operation: {}, resourceID: {}", action, resourceID);
    }
  }

  void propagateEvent(R resource, R oldResource) {
    var primaryResourceIdSet =
        configuration().getSecondaryToPrimaryMapper().toPrimaryResourceIDs(resource, oldResource);
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
      secondaryIDs = primaryToSecondaryIndex.getSecondaryResources(primaryResourceID);
      log.debug(
          "Using PrimaryToSecondaryIndex to find secondary resources for primary: {}. Found"
              + " secondary ids: {} ",
          primaryResourceID,
          secondaryIDs);
    } else {
      secondaryIDs = primaryToSecondaryMapper.toSecondaryResourceIDs(primary);
      log.debug(
          "Using PrimaryToSecondaryMapper to find secondary resources for primary. Found"
              + " secondary ids: {} ",
          secondaryIDs);
    }
    return secondaryIDs.stream()
        .map(this::get)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  @Override
  public void handleRecentResourceUpdate(
      ResourceID resourceID, R resource, R previousVersionOfResource) {
    handleRecentCreateOrUpdate(resource, previousVersionOfResource);
  }

  @Override
  public void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    handleRecentCreateOrUpdate(resource, null);
  }

  private void handleRecentCreateOrUpdate(R newResource, R previousVersion) {
    primaryToSecondaryIndex.onAddOrUpdate(newResource, previousVersion);
    temporaryResourceCache.putResource(newResource);
  }

  private boolean useSecondaryToPrimaryIndex() {
    return this.primaryToSecondaryMapper == null;
  }

  @Override
  public boolean allowsNamespaceChanges() {
    return configuration().followControllerNamespaceChanges();
  }

  private boolean eventAcceptedByFilter(
      ResourceAction action, R newObject, R oldObject, Boolean deletedFinalStateUnknown) {
    if (genericFilter != null && !genericFilter.accept(newObject)) {
      return false;
    }
    return switch (action) {
      case ADDED -> onAddFilter == null || onAddFilter.accept(newObject);
      case UPDATED -> onUpdateFilter == null || onUpdateFilter.accept(newObject, oldObject);
      case DELETED ->
          onDeleteFilter == null
              || onDeleteFilter.accept(newObject, Boolean.TRUE.equals(deletedFinalStateUnknown));
    };
  }
}
