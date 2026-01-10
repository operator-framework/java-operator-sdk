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
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.informer.TemporaryResourceCache.EventHandling;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_COMPARABLE_RESOURCE_VERSION;

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
    this(
        configuration,
        configuration.getKubernetesClient().orElse(context.getClient()),
        configuration.comparableResourceVersion());
  }

  InformerEventSource(InformerEventSourceConfiguration<R> configuration, KubernetesClient client) {
    this(configuration, client, DEFAULT_COMPARABLE_RESOURCE_VERSION);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private InformerEventSource(
      InformerEventSourceConfiguration<R> configuration,
      KubernetesClient client,
      boolean comparableResourceVersions) {
    super(
        configuration.name(),
        configuration
            .getGroupVersionKind()
            .map(gvk -> client.genericKubernetesResources(gvk.apiVersion(), gvk.getKind()))
            .orElseGet(() -> (MixedOperation) client.resources(configuration.getResourceClass())),
        configuration,
        comparableResourceVersions);
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
    onAddOrUpdate(Operation.ADD, newResource, null);
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
    onAddOrUpdate(Operation.UPDATE, newObject, oldObject);
  }

  @Override
  public synchronized void onDelete(R resource, boolean b) {
    if (log.isDebugEnabled()) {
      log.debug(
          "On delete event received for resource id: {} type: {}",
          ResourceID.fromResource(resource),
          resourceType().getSimpleName());
    }
    primaryToSecondaryIndex.onDelete(resource);
    temporaryResourceCache.onDeleteEvent(resource, b);
    if (acceptedByDeleteFilters(resource, b)) {
      propagateEvent(resource);
    }
  }

  @Override
  public void handleEvent(
      ResourceAction action,
      R resource,
      R oldResource,
      Boolean deletedFinalStateUnknown,
      boolean filterEvent) {
    propagateEvent(resource);
  }

  @Override
  public synchronized void start() {
    super.start();
    // this makes sure that on first reconciliation all resources are
    // present on the index
    manager().list().forEach(primaryToSecondaryIndex::onAddOrUpdate);
  }

  private synchronized void onAddOrUpdate(Operation operation, R newObject, R oldObject) {
    primaryToSecondaryIndex.onAddOrUpdate(newObject);
    var resourceID = ResourceID.fromResource(newObject);

    var eventHandling = temporaryResourceCache.onAddOrUpdateEvent(newObject);

    if (eventHandling != EventHandling.NEW) {
      log.debug(
          "{} event propagation for {}. Resource ID: {}",
          eventHandling == EventHandling.DEFER ? "Deferring" : "Skipping",
          operation,
          ResourceID.fromResource(newObject));
    } else if (eventAcceptedByFilter(operation, newObject, oldObject)) {
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
  public void handleRecentResourceUpdate(
      ResourceID resourceID, R resource, R previousVersionOfResource) {
    handleRecentCreateOrUpdate(resource);
  }

  @Override
  public void handleRecentResourceCreate(ResourceID resourceID, R resource) {
    handleRecentCreateOrUpdate(resource);
  }

  private void handleRecentCreateOrUpdate(R newResource) {
    primaryToSecondaryIndex.onAddOrUpdate(newResource);
    temporaryResourceCache.putResource(newResource);
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
