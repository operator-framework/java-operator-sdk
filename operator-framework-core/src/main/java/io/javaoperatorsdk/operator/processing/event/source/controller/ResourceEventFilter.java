package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;

/**
 * A functional interface to determine whether resource events should be processed by the SDK. This
 * allows users to more finely tuned which events trigger a reconciliation than was previously
 * possible (where the logic was limited to generation-based checking).
 *
 * @param <P> the type of custom resources handled by this filter
 */
@Deprecated(forRemoval = true)
@FunctionalInterface
public interface ResourceEventFilter<P extends HasMetadata> {

  /**
   * Determines whether the change between the old version of the resource and the new one needs to
   * be propagated to the controller or not.
   *
   * @param controller the target controller
   * @param oldResource the old version of the resource, null if no old resource available
   * @param newResource the new version of the resource
   * @return {@code true} if the change needs to be propagated to the controller, {@code false}
   *         otherwise
   */
  boolean acceptChange(Controller<P> controller, P oldResource, P newResource);

  /**
   * Combines this filter with the provided one with an AND logic, i.e. the resulting filter will
   * only accept the change if both this and the other filter accept it, reject it otherwise.
   *
   * @param other the possibly {@code null} other filter to combine this one with
   * @return a composite filter implementing the AND logic between this and the provided filter
   */
  default ResourceEventFilter<P> and(ResourceEventFilter<P> other) {
    return other == null ? this
        : (Controller<P> controller, P oldResource, P newResource) -> {
          boolean result = acceptChange(controller, oldResource, newResource);
          return result && other.acceptChange(controller, oldResource, newResource);
        };
  }

  /**
   * Combines this filter with the provided one with an OR logic, i.e. the resulting filter will
   * accept the change if any of this or the other filter accept it, rejecting it only if both
   * reject it.
   *
   * @param other the possibly {@code null} other filter to combine this one with
   * @return a composite filter implementing the OR logic between this and the provided filter
   */
  default ResourceEventFilter<P> or(ResourceEventFilter<P> other) {
    return other == null ? this
        : (Controller<P> controller, P oldResource, P newResource) -> {
          boolean result = acceptChange(controller, oldResource, newResource);
          return result || other.acceptChange(controller, oldResource, newResource);
        };
  }
}
