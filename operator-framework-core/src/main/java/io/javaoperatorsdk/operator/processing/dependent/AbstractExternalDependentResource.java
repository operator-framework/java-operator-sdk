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
package io.javaoperatorsdk.operator.processing.dependent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public abstract class AbstractExternalDependentResource<
        R, P extends HasMetadata, T extends EventSource<R, P>>
    extends AbstractEventSourceHolderDependentResource<R, P, T> {

  private final boolean isDependentResourceWithExplicitState =
      this instanceof DependentResourceWithExplicitState;
  private final boolean isBulkDependentResource = this instanceof BulkDependentResource;

  @SuppressWarnings("rawtypes")
  private DependentResourceWithExplicitState dependentResourceWithExplicitState;

  private InformerEventSource<?, P> externalStateEventSource;

  protected AbstractExternalDependentResource() {}

  @SuppressWarnings("unchecked")
  protected AbstractExternalDependentResource(Class<R> resourceType) {
    super(resourceType);
    if (isDependentResourceWithExplicitState) {
      dependentResourceWithExplicitState = (DependentResourceWithExplicitState<R, P, ?>) this;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void resolveEventSource(EventSourceRetriever<P> eventSourceRetriever) {
    super.resolveEventSource(eventSourceRetriever);
    if (isDependentResourceWithExplicitState) {
      final var eventSourceName =
          (String) dependentResourceWithExplicitState.eventSourceName().orElse(null);
      externalStateEventSource =
          (InformerEventSource<?, P>)
              eventSourceRetriever.getEventSourceFor(
                  dependentResourceWithExplicitState.stateResourceClass(), eventSourceName);
    }
  }

  @Override
  protected void onCreated(P primary, R created, Context<P> context) {
    super.onCreated(primary, created, context);
    if (this instanceof DependentResourceWithExplicitState) {
      handleExplicitStateCreation(primary, created, context);
    }
  }

  @Override
  public void delete(P primary, Context<P> context) {
    if (isDependentResourceWithExplicitState && !isBulkDependentResource) {
      var secondary = getSecondaryResource(primary, context);
      super.delete(primary, context);
      // deletes the state after the resource is deleted
      handleExplicitStateDelete(primary, secondary.orElse(null), context);
    } else {
      super.delete(primary, context);
    }
  }

  @SuppressWarnings({"unchecked", "unused"})
  private void handleExplicitStateDelete(P primary, R secondary, Context<P> context) {
    var res = dependentResourceWithExplicitState.stateResource(primary, secondary);
    context.getClient().resource(res).delete();
  }

  @SuppressWarnings({"rawtypes", "unchecked", "unused"})
  protected void handleExplicitStateCreation(P primary, R created, Context<P> context) {
    var resource = dependentResourceWithExplicitState.stateResource(primary, created);
    var stateResource =
        PrimaryUpdateAndCacheUtils.updateAndCacheSecondaryResource(
            resource,
            context,
            (RecentOperationCacheFiller) externalStateEventSource,
            toCreate -> context.getClient().resource(toCreate).create());
    if (externalStateEventSource != null) {
      ((RecentOperationCacheFiller) externalStateEventSource)
          .handleRecentResourceCreate(ResourceID.fromResource(primary), stateResource);
    }
  }

  @Override
  public Matcher.Result<R> match(R resource, P primary, Context<P> context) {
    var desired = desired(primary, context);
    return Matcher.Result.computed(resource.equals(desired), desired);
  }

  @SuppressWarnings("unchecked")
  public void deleteTargetResource(P primary, R resource, String key, Context<P> context) {
    if (isDependentResourceWithExplicitState) {
      context
          .getClient()
          .resource(dependentResourceWithExplicitState.stateResource(primary, resource))
          .delete();
    }
    handleDeleteTargetResource(primary, resource, key, context);
  }

  public void handleDeleteTargetResource(P primary, R resource, String key, Context<P> context) {
    throw new IllegalStateException("Override this method in case you manage an bulk resource");
  }

  @SuppressWarnings("rawtypes")
  protected InformerEventSource getExternalStateEventSource() {
    return externalStateEventSource;
  }

  @Override
  protected Optional<R> selectTargetSecondaryResource(
      Set<R> secondaryResources, P primary, Context<P> context) {
    R desired = desired(primary, context);
    List<R> targetResources;
    if (desired instanceof ExternalDependentIDProvider<?> desiredWithId) {
      targetResources =
          secondaryResources.stream()
              .filter(
                  r ->
                      ((ExternalDependentIDProvider<?>) r)
                          .externalResourceId()
                          .equals(desiredWithId.externalResourceId()))
              .toList();
    } else {
      throw new IllegalStateException(
          "Either implement ExternalDependentIDProvider or override this "
              + " (selectTargetSecondaryResource) method.");
    }
    if (targetResources.size() > 1) {
      throw new IllegalStateException(
          "More than one secondary resource related to primary: " + targetResources);
    }
    return targetResources.isEmpty() ? Optional.empty() : Optional.of(targetResources.get(0));
  }
}
