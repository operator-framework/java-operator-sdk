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
 * Filter applied to resource <i>update</i> events received by an informer-backed event source. It
 * receives both the new and the old version of the resource, so it can decide whether an update is
 * meaningful enough to be propagated and (potentially) trigger a reconciliation.
 *
 * <p>Filters only apply to informer-backed event sources; event sources that are not backed by an
 * informer have no filters.
 *
 * @param <R> the type of the updated resource
 */
@FunctionalInterface
public interface OnUpdateFilter<R> {

  /**
   * Decides whether the update event should be propagated, based on the new and old versions of the
   * resource.
   *
   * @param newResource the resource after the update
   * @param oldResource the resource before the update
   * @return {@code true} if the event should be <b>accepted</b> (propagated), {@code false} if it
   *     should be <b>dropped</b>
   */
  boolean accept(R newResource, R oldResource);

  /**
   * Composes this filter with another one using a logical <b>AND</b>: the resulting filter accepts
   * an event only when both this filter and the given filter accept it.
   *
   * @param onUpdateFilter the other filter to combine with this one
   * @return a filter that accepts an event only if both filters accept it
   */
  default OnUpdateFilter<R> and(OnUpdateFilter<R> onUpdateFilter) {
    return (newResource, oldResource) ->
        this.accept(newResource, oldResource) && onUpdateFilter.accept(newResource, oldResource);
  }

  /**
   * Composes this filter with another one using a logical <b>OR</b>: the resulting filter accepts
   * an event when either this filter or the given filter accepts it.
   *
   * @param onUpdateFilter the other filter to combine with this one
   * @return a filter that accepts an event if at least one of the filters accepts it
   */
  default OnUpdateFilter<R> or(OnUpdateFilter<R> onUpdateFilter) {
    return (newResource, oldResource) ->
        this.accept(newResource, oldResource) || onUpdateFilter.accept(newResource, oldResource);
  }

  /**
   * Negates this filter: the resulting filter accepts exactly the events this one drops, and drops
   * the events this one accepts.
   *
   * @return a filter that returns the logical <b>NOT</b> of this filter's result
   */
  default OnUpdateFilter<R> not() {
    return (newResource, oldResource) -> !this.accept(newResource, oldResource);
  }
}
