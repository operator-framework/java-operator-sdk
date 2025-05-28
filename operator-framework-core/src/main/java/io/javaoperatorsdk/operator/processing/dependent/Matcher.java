package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * Implement this interface to provide custom matching logic when determining whether secondary
 * resources match their desired state. This is used by some default implementations of the {@link
 * io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} interface, notably {@link
 * io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource}.
 *
 * @param <R> the type associated with the secondary resources we want to match
 * @param <P> the type associated with the primary resources with which the related {@link
 *     io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} implementation is
 *     associated
 */
public interface Matcher<R, P extends HasMetadata> {

  /**
   * Abstracts the matching result letting implementations also return the desired state if it has
   * been computed as part of their logic. This allows the SDK to avoid re-computing it if not
   * needed.
   *
   * @param <R> the type associated with the secondary resources we want to match
   */
  interface Result<R> {

    /**
     * Whether or not the actual resource matched the desired state
     *
     * @return {@code true} if the observed resource matched the desired state, {@code false}
     *     otherwise
     */
    boolean matched();

    /**
     * Retrieves the associated desired state if it has been computed during the matching process or
     * empty if not.
     *
     * @return an {@link Optional} holding the desired state if it has been computed during the
     *     matching process or {@link Optional#empty()} if not
     */
    default Optional<R> computedDesired() {
      return Optional.empty();
    }

    /**
     * Creates a result stating only whether the resource matched the desired state without having
     * computed the desired state.
     *
     * @param matched whether the actual resource matched the desired state
     * @return a {@link Result} with an empty computed desired state
     * @param <T> the type of resources being matched
     */
    static <T> Result<T> nonComputed(boolean matched) {
      return () -> matched;
    }

    /**
     * Creates a result stating whether the resource matched and the associated computed desired
     * state so that the SDK can use it downstream without having to recompute it.
     *
     * @param matched whether the actual resource matched the desired state
     * @param computedDesired the associated desired state as computed during the matching process
     * @return a {@link Result} with the associated desired state
     * @param <T> the type of resources being matched
     */
    static <T> Result<T> computed(boolean matched, T computedDesired) {
      return new Result<>() {
        @Override
        public boolean matched() {
          return matched;
        }

        @Override
        public Optional<T> computedDesired() {
          return Optional.of(computedDesired);
        }
      };
    }
  }

  /**
   * Determines whether the specified secondary resource matches the desired state as defined from
   * the specified primary resource, given the specified {@link Context}.
   *
   * @param actualResource the resource we want to determine whether it's matching the desired state
   * @param primary the primary resource from which the desired state is inferred
   * @param context the context in which the resource is being matched
   * @return a {@link Result} encapsulating whether the resource matched its desired state and this
   *     associated state if it was computed as part of the matching process. Use the static
   *     convenience methods ({@link Result#nonComputed(boolean)} and {@link
   *     Result#computed(boolean, Object)}) to create your return {@link Result}.
   */
  Result<R> match(R actualResource, P primary, Context<P> context);
}
