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
package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.TemporaryResourceCache.EventHandling;

import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.handleKubernetesClientException;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;
import static io.javaoperatorsdk.operator.processing.event.source.controller.InternalEventFilters.*;

public class ControllerEventSource<T extends HasMetadata>
    extends ManagedInformerEventSource<T, T, ControllerConfiguration<T>>
    implements ResourceEventHandler<T> {

  private static final Logger log = LoggerFactory.getLogger(ControllerEventSource.class);
  public static final String NAME = "ControllerResourceEventSource";

  private final Controller<T> controller;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public ControllerEventSource(Controller<T> controller) {
    super(
        NAME,
        controller.getCRClient(),
        controller.getConfiguration(),
        controller.getConfiguration().getInformerConfig().isComparableResourceVersions());
    this.controller = controller;

    final var config = controller.getConfiguration();
    OnUpdateFilter internalOnUpdateFilter =
        onUpdateFinalizerNeededAndApplied(controller.useFinalizer(), config.getFinalizerName())
            .or(onUpdateGenerationAware(config.isGenerationAware()))
            .or(onUpdateMarkedForDeletion());

    // by default the on add should be processed in all cases regarding internal filters
    final var informerConfig = config.getInformerConfig();
    Optional.ofNullable(informerConfig.getOnAddFilter()).ifPresent(this::setOnAddFilter);
    Optional.ofNullable(informerConfig.getOnUpdateFilter())
        .ifPresentOrElse(
            filter -> setOnUpdateFilter(filter.and(internalOnUpdateFilter)),
            () -> setOnUpdateFilter(internalOnUpdateFilter));
    Optional.ofNullable(informerConfig.getGenericFilter()).ifPresent(this::setGenericFilter);
    setControllerConfiguration(config);
  }

  @Override
  public synchronized void start() {
    try {
      super.start();
    } catch (KubernetesClientException e) {
      handleKubernetesClientException(e, controller.getConfiguration().getResourceTypeName());
      throw e;
    }
  }

  @Override
  public synchronized void handleEvent(
      ResourceAction action,
      T resource,
      T oldResource,
      Boolean deletedFinalStateUnknown,
      boolean filterEvent) {
    try {
      if (log.isDebugEnabled()) {
        log.debug(
            "Event received for resource: {} version: {} uuid: {} action: {} filter event: {}",
            ResourceID.fromResource(resource),
            getVersion(resource),
            resource.getMetadata().getUid(),
            action,
            filterEvent);
        log.trace("Event Old resource: {},\n new resource: {}", oldResource, resource);
      }
      MDCUtils.addResourceInfo(resource);
      controller.getEventSourceManager().broadcastOnResourceEvent(action, resource, oldResource);
      if (isAcceptedByFilters(action, resource, oldResource) && !filterEvent) {
        if (deletedFinalStateUnknown != null) {
          getEventHandler()
              .handleEvent(
                  new ResourceDeleteEvent(
                      action,
                      ResourceID.fromResource(resource),
                      resource,
                      deletedFinalStateUnknown));
        } else {
          getEventHandler()
              .handleEvent(new ResourceEvent(action, ResourceID.fromResource(resource), resource));
        }
      } else {
        log.debug("Skipping event handling resource {}", ResourceID.fromResource(resource));
      }
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  private boolean isAcceptedByFilters(ResourceAction action, T resource, T oldResource) {
    // delete event is filtered for generic filter only.
    if (genericFilter != null && !genericFilter.accept(resource)) {
      return false;
    }
    switch (action) {
      case ADDED:
        return onAddFilter == null || onAddFilter.accept(resource);
      case UPDATED:
        return onUpdateFilter.accept(resource, oldResource);
    }
    return true;
  }

  @Override
  public void onAdd(T resource) {
    var handling = temporaryResourceCache.onAddOrUpdateEvent(resource);
    handleEvent(ResourceAction.ADDED, resource, null, null, handling != EventHandling.NEW);
  }

  @Override
  public void onUpdate(T oldCustomResource, T newCustomResource) {
    var handling = temporaryResourceCache.onAddOrUpdateEvent(newCustomResource);
    handleEvent(
        ResourceAction.UPDATED,
        newCustomResource,
        oldCustomResource,
        null,
        handling != EventHandling.NEW);
  }

  @Override
  public void onDelete(T resource, boolean deletedFinalStateUnknown) {
    temporaryResourceCache.onDeleteEvent(resource, deletedFinalStateUnknown);
    // delete event is quite special here, that requires special care, since we clean up caches on
    // delete event.
    handleEvent(ResourceAction.DELETED, resource, null, deletedFinalStateUnknown, false);
  }

  @Override
  public Optional<T> getSecondaryResource(T primary) {
    throw new IllegalStateException("This method should not be called here. Primary: " + primary);
  }

  @Override
  public Set<T> getSecondaryResources(T primary) {
    throw new IllegalStateException("This method should not be called here. Primary: " + primary);
  }

  @Override
  public void setOnDeleteFilter(OnDeleteFilter<? super T> onDeleteFilter) {
    throw new IllegalStateException(
        "onDeleteFilter is not supported for controller resource event source");
  }

  @Override
  public String name() {
    return NAME;
  }
}
