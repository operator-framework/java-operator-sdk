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
package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

/**
 * Creates an event source to trigger your reconciler whenever something happens to a secondary or
 * external resource that should cause a reconciliation of the primary resource. EventSource
 * generalizes the concept of Informers and extends it to external (i.e. non Kubernetes) resources.
 *
 * @param <R> the resource type that this EventSource is associated with
 * @param <P> the primary resource type which reconciler needs to be triggered when events occur on
 *     resources of type R
 */
public interface EventSource<R, P extends HasMetadata>
    extends LifecycleAware, EventSourceHealthIndicator {

  static String generateName(EventSource<?, ?> eventSource) {
    return eventSource.getClass().getName() + "@" + Integer.toHexString(eventSource.hashCode());
  }

  /**
   * Sets the {@link EventHandler} that is linked to your reconciler when this EventSource is
   * registered.
   *
   * @param handler the {@link EventHandler} associated with your reconciler
   */
  void setEventHandler(EventHandler handler);

  /**
   * Retrieves the EventSource's name so that it can be referred to
   *
   * @return the EventSource's name
   */
  default String name() {
    return generateName(this);
  }

  /**
   * Retrieves the EventSource's starting priority
   *
   * @return the EventSource's starting priority
   * @see EventSourceStartPriority
   */
  default EventSourceStartPriority priority() {
    return EventSourceStartPriority.DEFAULT;
  }

  /**
   * Retrieves the resource type associated with this ResourceEventSource
   *
   * @return the resource type associated with this ResourceEventSource
   */
  Class<R> resourceType();

  /**
   * Retrieves the optional <em>unique</em> secondary resource associated with the specified primary
   * resource. Note that this operation will fail if multiple resources are associated with the
   * specified primary resource.
   *
   * @param primary the primary resource for which the secondary resource is requested
   * @return the secondary resource associated with the specified primary resource
   * @throws IllegalStateException if multiple resources are associated with the primary one
   */
  default Optional<R> getSecondaryResource(P primary) {
    var resources = getSecondaryResources(primary);
    if (resources.isEmpty()) {
      return Optional.empty();
    } else if (resources.size() == 1) {
      return Optional.of(resources.iterator().next());
    } else {
      throw new IllegalStateException("More than 1 secondary resource related to primary");
    }
  }

  /**
   * Retrieves a potential empty set of resources tracked by this EventSource associated with the
   * specified primary resource
   *
   * @param primary the primary resource for which the secondary resource is requested
   * @return the set of secondary resources associated with the specified primary
   */
  Set<R> getSecondaryResources(P primary);

  void setOnAddFilter(OnAddFilter<? super R> onAddFilter);

  void setOnUpdateFilter(OnUpdateFilter<? super R> onUpdateFilter);

  void setOnDeleteFilter(OnDeleteFilter<? super R> onDeleteFilter);

  void setGenericFilter(GenericFilter<? super R> genericFilter);

  @Override
  default Status getStatus() {
    return Status.UNKNOWN;
  }
}
