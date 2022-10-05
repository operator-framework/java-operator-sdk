package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;

/**
 * Manages dynamic number of resources created for a primary resource. Since the point of a bulk
 * dependent resource is to manage the number of secondary resources dynamically it implement
 * {@link Creator} and {@link Deleter} interfaces out of the box. A concrete dependent resource can
 * implement additionally also {@link Updater}.
 */
public interface BulkDependentResource<R, P extends HasMetadata, T>
    extends Creator<R, P>, Deleter<P> {

  /**
   * @return number of resources to create
   */
  Set<T> targetKeys(P primary, Context<P> context);

  Map<T, R> getSecondaryResources(P primary, Context<P> context);

  R desired(P primary, T key, Context<P> context);

  // todo add back key?
  /**
   * Used to delete resource if the desired count is lower than the actual count of a resource.
   *
   * @param primary resource
   * @param resource actual resource from the cache for the index
   * @param key key of the resource
   * @param context actual context
   */
  void deleteBulkResource(P primary, R resource, T key, Context<P> context);

  /**
   * Determines whether the specified secondary resource matches the desired state with target index
   * of a bulk resource as defined from the specified primary resource, given the specified
   * {@link Context}.
   *
   * @param actualResource the resource we want to determine whether it's matching the desired state
   * @param primary the primary resource from which the desired state is inferred
   * @param key key of the resource
   * @param context the context in which the resource is being matched
   * @return a {@link Result} encapsulating whether the resource matched its desired state and this
   *         associated state if it was computed as part of the matching process. Use the static
   *         convenience methods ({@link Result#nonComputed(boolean)} and
   *         {@link Result#computed(boolean, Object)})
   */
  Result<R> match(R actualResource, P primary, T key, Context<P> context);

}
