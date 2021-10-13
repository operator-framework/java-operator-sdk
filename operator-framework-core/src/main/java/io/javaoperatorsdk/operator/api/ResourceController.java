package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

public interface ResourceController<R extends CustomResource> {

  /**
   * The implementation should delete the associated component(s). Note that this is method is
   * called when an object is marked for deletion. After it's executed the custom resource finalizer
   * is automatically removed by the framework; unless the return value is
   * {@link DeleteControl#noFinalizerRemoval()}, which indicates that the controller has determined
   * that the resource should not be deleted yet, in which case it is up to the controller to
   * restore the resource's status so that it's not marked for deletion anymore.
   *
   * <p>
   * It's important that this method be idempotent, as it could be called several times, depending
   * on the conditions and the controller's configuration (for example, if the controller is
   * configured to not use a finalizer but the resource does have finalizers, it might be be
   * "offered" again for deletion several times until the finalizers are all removed.
   *
   * @param resource the resource that is marked for deletion
   * @param context the context with which the operation is executed
   * @return {@link DeleteControl#defaultDelete()} - so the finalizer is automatically removed after
   *         the call. {@link DeleteControl#noFinalizerRemoval()} if you don't want to remove the
   *         finalizer to indicate that the resource should not be deleted after all, in which case
   *         the controller should restore the resource's state appropriately.
   */
  default DeleteControl deleteResource(R resource, Context<R> context) {
    return DeleteControl.defaultDelete();
  }

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible. Also always use the custom resource
   * parameter (not the custom resource that might be in the events)
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
  UpdateControl<R> createOrUpdateResource(R resource, Context<R> context);

  /**
   * In init typically you might want to register event sources.
   *
   * @param eventSourceManager the {@link EventSourceManager} which handles this controller and with
   *        which event sources can be registered
   */
  default void init(EventSourceManager eventSourceManager) {}
}
