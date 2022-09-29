package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;

/**
 * Manages dynamic number of resources created for a primary resource. Since the point of a bulk
 * dependent resource is to manage the number of secondary resources dynamically it implement
 * {@link Creator} and {@link Deleter} interfaces out of the box. A concrete dependent resource can
 * implement additionally also {@link Updater}.
 */
public interface BulkDependentResource<R, P extends HasMetadata> extends Creator<R, P>, Deleter<P> {

  /**
   * @return number of resources to create
   */
  int count(P primary, Context<P> context);

  R desired(P primary, int index, Context<P> context);

  /**
   * Used to delete resource if the desired count is lower than the actual count of a resource.
   *
   * @param primary resource
   * @param resource actual resource from the cache for the index
   * @param i index of the resource
   * @param context actual context
   */
  void deleteBulkResourceWithIndex(P primary, R resource, int i, Context<P> context);

  ResourceDiscriminator<R, P> getResourceDiscriminator(int index);

}
