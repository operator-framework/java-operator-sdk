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

  public static final String PREVIOUS_ANNOTATION_KEY = "javaoperatorsdk.io/previous";
  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);
  // we need direct control for the indexer to propagate the just update resource also to the index
  private final PrimaryToSecondaryIndex<R> primaryToSecondaryIndex;
  private final PrimaryToSecondaryMapper<P> primaryToSecondaryMapper;
  private final String id = UUID.randomUUID().toString();

  public InformerEventSource(
      InformerEventSourceConfiguration<R> configuration, EventSourceContext<P> context) {
    this(configuration, configuration.getKubernetesClient().orElse(context.getClient()));
  }

  // visible for testing
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
    if (log.isDebugEnabled()) {
      log.debug(
          "On add event received for resource id: {} type: {} version: {}",
          ResourceID.fromResource(newResource),
          resourceType().getSimpleName(),
          newResource.getMetadata().getResourceVersion());
    }
    primaryToSecondaryIndex.onAddOrUpdate(newResource);
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
    primaryToSecondaryIndex.onAddOrUpdate(newObject);
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
    primaryToSecondaryIndex.onDelete(resource);
    super.onDelete(resource, b);
    if (acceptedByDeleteFilters(resource, b)) {
      propagateEvent(resource);
    }
  }

  @Override
  public synchronized void start() {
    super.start();
    // this makes sure that on first reconciliation all resources are
    // present on the index
    manager().list().forEach(primaryToSecondaryIndex::onAddOrUpdate);
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
    return !temporaryResourceCache.isNewerThenKnownResource(newObject, resourceID);
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
          "Using PrimaryToSecondaryMapper to find secondary resources for primary: {}. Found"
              + " secondary ids: {} ",
          primary,
          secondaryIDs);
    }
    return secondaryIDs.stream()
        .map(this::get)
        .flatMap(Optional::stream)
        .collect(Collectors.toSet());
  }

  @Override
  public synchronized void handleRecentResourceUpdate(
      ResourceID resourceID, R resource, R previousVersionOfResource) {
    handleRecentCreateOrUpdate(Operation.UPDATE, resource, previousVersionOfResource);
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    handleRecentCreateOrUpdate(Operation.ADD, resource, null);
  }

  private void handleRecentCreateOrUpdate(Operation operation, R newResource, R oldResource) {
    primaryToSecondaryIndex.onAddOrUpdate(newResource);
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

  private enum Operation {
    ADD,
    UPDATE
  }
}
