package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Reconciler<R extends HasMetadata> {

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible.
   *
   * @param resource the resource that has been created or updated
   * @param context the context with which the operation is executed
   * @return The resource is updated in api server if the return value is not
   *         {@link UpdateControl#noUpdate()}. This the common use cases. However in cases, for
   *         example the operator is restarted, and we don't want to have an update call to k8s api
   *         to be made unnecessarily, by returning {@link UpdateControl#noUpdate()} this update can
   *         be skipped. <b>However we will always call an update if there is no finalizer on object
   *         and it's not marked for deletion.</b>
   */
  UpdateControl<R> reconcile(R resource, Context context);

  /**
   * Note that this method is used in combination with finalizers. If automatic finalizer handling is
   * turned off for the controller, this method is not called.
   *
   * The implementation should delete the associated component(s). This method is called when an
   * object is marked for deletion. After it's executed the custom resource finalizer is
   * automatically removed by the framework; unless the return value is
   * {@link DeleteControl#noFinalizerRemoval()}, which indicates that the controller has determined
   * that the resource should not be deleted yet. This is usually a corner case, when a cleanup is
   * tried again eventually.
   *
   * <p>
   * It's important that implementation of this method to be idempotent, since it can be called
   * several times.
   *
   * @param resource the resource that is marked for deletion
   * @param context the context with which the operation is executed
   * @return {@link DeleteControl#defaultDelete()} - so the finalizer is automatically removed after
   *         the call. {@link DeleteControl#noFinalizerRemoval()} if you don't want to remove the
   *         finalizer to indicate that the resource should not be deleted after all, in which case
   *         the controller should restore the resource's state appropriately.
   */
  default DeleteControl cleanup(R resource, Context context) {
    return DeleteControl.defaultDelete();
  }

}
