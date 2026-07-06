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
 * Filter applied to every kind of event (add, update <b>and</b> delete) received by an
 * informer-backed event source. It is a convenient way to express a condition that is independent
 * of the event type.
 *
 * <p>When both a type-specific filter (such as {@link OnAddFilter}, {@link OnUpdateFilter} or
 * {@link OnDeleteFilter}) and a {@code GenericFilter} are configured, <b>both</b> must accept an
 * event for it to be propagated.
 *
 * <p>Filters only apply to informer-backed event sources; event sources that are not backed by an
 * informer have no filters.
 *
 * @param <R> the type of the resource the events relate to
 */
@FunctionalInterface
public interface GenericFilter<R> {

  /**
   * Decides whether an event concerning the given resource should be propagated.
   *
   * @param resource the resource the event relates to
   * @return {@code true} if the event should be <b>accepted</b> (propagated), {@code false} if it
   *     should be <b>dropped</b>
   */
  boolean accept(R resource);

  /**
   * Composes this filter with another one using a logical <b>AND</b>: the resulting filter accepts
   * an event only when both this filter and the given filter accept it.
   *
   * @param genericFilter the other filter to combine with this one
   * @return a filter that accepts an event only if both filters accept it
   */
  default GenericFilter<R> and(GenericFilter<R> genericFilter) {
    return (resource) -> this.accept(resource) && genericFilter.accept(resource);
  }

  /**
   * Composes this filter with another one using a logical <b>OR</b>: the resulting filter accepts
   * an event when either this filter or the given filter accepts it.
   *
   * @param genericFilter the other filter to combine with this one
   * @return a filter that accepts an event if at least one of the filters accepts it
   */
  default GenericFilter<R> or(GenericFilter<R> genericFilter) {
    return (resource) -> this.accept(resource) || genericFilter.accept(resource);
  }

  /**
   * Negates this filter: the resulting filter accepts exactly the events this one drops, and drops
   * the events this one accepts.
   *
   * @return a filter that returns the logical <b>NOT</b> of this filter's result
   */
  default GenericFilter<R> not() {
    return (resource) -> !this.accept(resource);
  }
}
