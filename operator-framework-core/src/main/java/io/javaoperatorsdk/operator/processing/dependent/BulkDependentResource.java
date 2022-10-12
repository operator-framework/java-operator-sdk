package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Map;

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
public interface BulkDependentResource<R, P extends HasMetadata>
    extends Creator<R, P>, Deleter<P> {

  /**
   * Retrieves a map of desired secondary resources associated with the specified primary resource,
   * identified by an arbitrary key.
   *
   * @param primary the primary resource with which we want to identify which secondary resources
   *        are associated
   * @param context the {@link Context} associated with the current reconciliation
   * @return a Map associating desired secondary resources with the specified primary via arbitrary
   *         identifiers
   */
  Map<String, R> desiredResources(P primary, Context<P> context);

  /**
   * Retrieves the actual secondary resources currently existing on the server and associated with
   * the specified primary resource.
   *
   * @param primary the primary resource for which we want to retrieve the associated secondary
   *        resources
   * @param context the {@link Context} associated with the current reconciliation
   * @return a Map associating actual secondary resources with the specified primary via arbitrary
   *         identifiers
   */
  Map<String, R> getSecondaryResources(P primary, Context<P> context);

  /**
   * Used to delete resource if the desired count is lower than the actual count of a resource.
   *
   * @param primary resource
   * @param resource actual resource from the cache for the index
   * @param key key of the resource
   * @param context actual context
   */
  void deleteBulkResource(P primary, R resource, String key, Context<P> context);

  /**
   * Determines whether the specified secondary resource matches the desired state with target index
   * of a bulk resource as defined from the specified primary resource, given the specified
   * {@link Context}.
   *
   * @param actualResource the resource we want to determine whether it's matching the desired state
   * @param desired the resource's desired state
   * @param primary the primary resource from which the desired state is inferred
   * @param context the context in which the resource is being matched
   * @return a {@link Result} encapsulating whether the resource matched its desired state and this
   *         associated state if it was computed as part of the matching process. Use the static
   *         convenience methods ({@link Result#nonComputed(boolean)} and
   *         {@link Result#computed(boolean, Object)})
   */
  default Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    return Matcher.Result.computed(desired.equals(actualResource), desired);
  }
}
