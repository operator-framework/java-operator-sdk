package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ErrorStatusHandler<T extends HasMetadata> {

  /**
   * <p>
   * Reconcile can implement this interface in order to update the status sub-resource in the case
   * when the last reconciliation retry attempt is failed on the Reconciler. In that case the
   * updateErrorStatus is called automatically.
   * <p>
   * The result of the method call is used to make a status update on the custom resource. This is
   * always a sub-resource update request, so no update on custom resource itself (like spec of
   * metadata) happens. Note that this update request will also produce an event, and will result in
   * a reconciliation if the controller is not generation aware.
   * <p>
   * Note that the scope of this feature is only the reconcile method of the reconciler, since there
   * should not be updates on custom resource after it is marked for deletion.
   *
   * @param resource to update the status on
   * @param e exception thrown from the reconciler
   * @return the updated resource
   */
  T updateErrorStatus(T resource, RuntimeException e);

}
