package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public interface DependentResource<R, P extends HasMetadata> {
  default EventSource initEventSource(EventSourceContext<P> context) {
    throw new IllegalStateException("Must be implemented if not automatically provided by the SDK");
  }

  @SuppressWarnings("unchecked")
  default Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromInterface(getClass());
  }

  default void delete(R fetched, P primary, Context context) {}

  /**
   * Computes the desired state of the dependent based on the state provided by the specified
   * primary resource.
   * 
   * The default implementation returns {@code empty} which corresponds to the case where the
   * associated dependent should never be created by the associated reconciler or that the global
   * state of the cluster doesn't allow for the resource to be created at this point.
   * 
   * @param primary the primary resource associated with the reconciliation process
   * @param context the {@link Context} associated with the reconciliation process
   * @return an instance of the dependent resource matching the desired state specified by the
   *         primary resource or {@code empty} if the dependent shouldn't be created at this point
   *         (or ever)
   */
  default Optional<R> desired(P primary, Context context) {
    return Optional.empty();
  }

  /**
   * Checks whether the actual resource as fetched from the cluster matches the desired state
   * expressed by the specified primary resource.
   * 
   * The default implementation always return {@code true}, which corresponds to the behavior where
   * the dependent never needs to be updated after it's been created.
   * 
   * Note that failure to properly implement this method will lead to infinite loops. In particular,
   * for typical Kubernetes resource implementations, simply calling
   * {@code desired(primary, context).equals(actual)} is not enough because metadata will usually be
   * different.
   * 
   * @param actual the current state of the resource as fetched from the cluster
   * @param primary the primary resource associated with the reconciliation request
   * @param context the {@link Context} associated with the reconciliation request
   * @return {@code true} if the actual state of the resource matches the desired state expressed by
   *         the specified primary resource, {@code false} otherwise
   */
  default boolean match(R actual, P primary, Context context) {
    return true;
  }
}
