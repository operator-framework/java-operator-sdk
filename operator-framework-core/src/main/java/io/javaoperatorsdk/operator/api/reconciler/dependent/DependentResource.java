package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

/**
 * An interface to implement and provide dependent resource support.
 *
 * @param <R> the dependent resource type
 * @param <P> the associated primary resource type
 */
public interface DependentResource<R, P extends HasMetadata> {

  /**
   * Reconciles the dependent resource given the desired primary state
   *
   * @param primary the primary resource for which we want to reconcile the dependent state
   * @param context {@link Context} providing useful contextual information
   * @return a {@link ReconcileResult} providing information about the reconciliation result
   */
  ReconcileResult<R> reconcile(P primary, Context<P> context);

  /**
   * Retrieves the resource type associated with this DependentResource
   *
   * @return the resource type associated with this DependentResource
   */
  Class<R> resourceType();

  /**
   * Dependent resources are designed to by default provide event sources. There are cases where it
   * might not:
   * <ul>
   * <li>If an event source is shared between multiple dependent resources. In this case only one or
   * none of the dependent resources sharing the event source should provide one.</li>
   * <li>Some special implementation of an event source. That just execute some action might not
   * provide one.</li>
   * </ul>
   *
   * @param eventSourceContext context of event source initialization
   * @return an optional event source
   */
  default Optional<ResourceEventSource<R, P>> eventSource(
      EventSourceContext<P> eventSourceContext) {
    return Optional.empty();
  }

  /**
   * Calling this method, instructs the implementation to not provide an event source, even if it
   * normally does.
   */
  void doNotProvideEventSource();


  default Optional<R> getSecondaryResource(P primary, Context<P> context) {
    return Optional.empty();
  }

  /**
   * Computes a default name for the specified DependentResource class
   *
   * @param dependentResourceClass the DependentResource class for which we want to compute a
   *        default name
   * @return the default name for the specified DependentResource class
   */
  @SuppressWarnings("rawtypes")
  static String defaultNameFor(Class<? extends DependentResource> dependentResourceClass) {
    return dependentResourceClass.getName();
  }
}
