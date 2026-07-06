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
package io.javaoperatorsdk.operator.processing.event.source.filter;

/**
 * Filter applied to resource <i>delete</i> events received by an informer-backed event source. It
 * lets you decide whether the deletion of a resource should be propagated and (potentially) trigger
 * a reconciliation.
 *
 * <p>Filters only apply to informer-backed event sources; event sources that are not backed by an
 * informer have no filters.
 *
 * @param <R> the type of the deleted resource
 */
@FunctionalInterface
public interface OnDeleteFilter<R> {

  /**
   * Decides whether the delete event for the given resource should be propagated.
   *
   * @param hasMetadata the resource that was deleted
   * @param deletedFinalStateUnknown {@code true} when the informer missed the delete event and the
   *     final state of the resource is unknown (the object may be stale), {@code false} when the
   *     deletion was observed with the resource's last known state
   * @return {@code true} if the event should be <b>accepted</b> (propagated), {@code false} if it
   *     should be <b>dropped</b>
   */
  boolean accept(R hasMetadata, Boolean deletedFinalStateUnknown);

  /**
   * Composes this filter with another one using a logical <b>AND</b>: the resulting filter accepts
   * an event only when both this filter and the given filter accept it.
   *
   * @param OnDeleteFilter the other filter to combine with this one
   * @return a filter that accepts an event only if both filters accept it
   */
  default OnDeleteFilter<R> and(OnDeleteFilter<R> OnDeleteFilter) {
    return (resource, deletedFinalStateUnknown) ->
        this.accept(resource, deletedFinalStateUnknown)
            && OnDeleteFilter.accept(resource, deletedFinalStateUnknown);
  }

  /**
   * Composes this filter with another one using a logical <b>OR</b>: the resulting filter accepts
   * an event when either this filter or the given filter accepts it.
   *
   * @param OnDeleteFilter the other filter to combine with this one
   * @return a filter that accepts an event if at least one of the filters accepts it
   */
  default OnDeleteFilter<R> or(OnDeleteFilter<R> OnDeleteFilter) {
    return (resource, deletedFinalStateUnknown) ->
        this.accept(resource, deletedFinalStateUnknown)
            || OnDeleteFilter.accept(resource, deletedFinalStateUnknown);
  }

  /**
   * Negates this filter: the resulting filter accepts exactly the events this one drops, and drops
   * the events this one accepts.
   *
   * @return a filter that returns the logical <b>NOT</b> of this filter's result
   */
  default OnDeleteFilter<R> not() {
    return (resource, deletedFinalStateUnknown) -> !this.accept(resource, deletedFinalStateUnknown);
  }
}
