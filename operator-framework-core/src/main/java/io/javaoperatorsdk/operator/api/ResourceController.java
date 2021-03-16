package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

public interface ResourceController<R extends CustomResource> {

  /**
   * The implementation should delete the associated component(s). Note that this is method is
   * called when an object is marked for deletion. After it's executed the custom resource finalizer
   * is automatically removed by the framework; unless the return value is {@link
   * DeleteControl#NO_FINALIZER_REMOVAL} - note that this is almost never the case. It's important
   * to have the implementation also idempotent, in the current implementation to cover all edge
   * cases actually will be executed mostly twice.
   *
   * @param resource
   * @return {@link DeleteControl#DEFAULT_DELETE} - so the finalizer is automatically removed after
   *     the call. {@link DeleteControl#NO_FINALIZER_REMOVAL} if you don't want to remove the
   *     finalizer. Note that this is ALMOST NEVER the case.
   */
  default DeleteControl deleteResource(R resource, Context<R> context) {
    return DeleteControl.DEFAULT_DELETE;
  }

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible. Also always use the custom resource
   * parameter (not the custom resource that might be in the events)
   *
   * @return The resource is updated in api server if the return value is not {@link
   *     UpdateControl#noUpdate()}. This the common use cases. However in cases, for example the
   *     operator is restarted, and we don't want to have an update call to k8s api to be made
   *     unnecessarily, by returning {@link UpdateControl#noUpdate()} this update can be skipped.
   *     <b>However we will always call an update if there is no finalizer on object and it's not
   *     marked for deletion.</b>
   */
  UpdateControl<R> createOrUpdateResource(R resource, Context<R> context);

  /**
   * In init typically you might want to register event sources.
   *
   * @param eventSourceManager
   */
  default void init(EventSourceManager eventSourceManager) {}
}
