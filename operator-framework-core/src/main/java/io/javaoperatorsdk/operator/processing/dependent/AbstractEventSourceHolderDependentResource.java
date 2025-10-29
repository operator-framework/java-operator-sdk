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

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceNotFoundException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceReferencer;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@Ignore
public abstract class AbstractEventSourceHolderDependentResource<
        R, P extends HasMetadata, T extends EventSource<R, P>>
    extends AbstractDependentResource<R, P> implements EventSourceReferencer<P> {

  private T eventSource;
  private final Class<R> resourceType;
  private boolean isCacheFillerEventSource;
  protected String eventSourceNameToUse;

  @SuppressWarnings("unchecked")
  protected AbstractEventSourceHolderDependentResource() {
    this(null, null);
  }

  protected AbstractEventSourceHolderDependentResource(Class<R> resourceType) {
    this(resourceType, null);
  }

  protected AbstractEventSourceHolderDependentResource(Class<R> resourceType, String name) {
    super(name);
    if (resourceType == null) {
      this.resourceType = (Class<R>) Utils.getTypeArgumentFromHierarchyByIndex(getClass(), 0);
    } else {
      this.resourceType = resourceType;
    }
  }

  /**
   * Method is synchronized since when used in case of dynamic registration (thus for activation
   * conditions) can be called concurrently to create the target event source. In that case only one
   * instance should be created, since this also sets the event source, and dynamic registration
   * will just start one with the same name. So if this would not be synchronized it could happen
   * that multiple event sources would be created and only one started and registered. Note that
   * this method does not start the event source, so no blocking IO is involved.
   */
  public synchronized Optional<T> eventSource(EventSourceContext<P> context) {
    // some sub-classes (e.g. KubernetesDependentResource) can have their event source created
    // before this method is called in the managed case, so only create the event source if it
    // hasn't already been set.
    // The filters are applied automatically only if event source is created automatically. So if an
    // event source
    // is shared between dependent resources this does not override the existing filters.

    if (eventSource == null && eventSourceNameToUse == null) {
      setEventSource(createEventSource(context));
    }
    return Optional.ofNullable(eventSource);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void resolveEventSource(EventSourceRetriever<P> eventSourceRetriever) {
    if (eventSourceNameToUse != null && eventSource == null) {
      final var source =
          eventSourceRetriever.getEventSourceFor(resourceType(), eventSourceNameToUse);
      if (source == null) {
        throw new EventSourceNotFoundException(eventSourceNameToUse);
      }
      setEventSource((T) source);
    }
  }

  /**
   * To make this backwards compatible even for respect of overriding
   *
   * @param context for event sources
   * @return event source instance
   */
  public T initEventSource(EventSourceContext<P> context) {
    return eventSource(context).orElseThrow();
  }

  @Override
  public void useEventSourceWithName(String name) {
    this.eventSourceNameToUse = name;
  }

  @Override
  public Class<R> resourceType() {
    return resourceType;
  }

  protected abstract T createEventSource(EventSourceContext<P> context);

  public void setEventSource(T eventSource) {
    isCacheFillerEventSource = eventSource instanceof RecentOperationCacheFiller;
    this.eventSource = eventSource;
  }

  public Optional<T> eventSource() {
    return Optional.ofNullable(eventSource);
  }

  protected void onCreated(P primary, R created, Context<P> context) {
    if (isCacheFillerEventSource) {
      recentOperationCacheFiller()
          .handleRecentResourceCreate(ResourceID.fromResource(primary), created);
    }
  }

  protected void onUpdated(P primary, R updated, R actual, Context<P> context) {
    if (isCacheFillerEventSource) {
      recentOperationCacheFiller()
          .handleRecentResourceUpdate(ResourceID.fromResource(primary), updated, actual);
    }
  }

  @SuppressWarnings("unchecked")
  private RecentOperationCacheFiller<R> recentOperationCacheFiller() {
    return (RecentOperationCacheFiller<R>) eventSource;
  }
}
