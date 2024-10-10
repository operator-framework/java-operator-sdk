package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Cleaner<P extends HasMetadata> {

  /**
   * This method turns on automatic finalizer usage.
   * <p>
   * The implementation should delete the associated component(s). This method is called when an
   * object is marked for deletion. After it's executed the custom resource finalizer is
   * automatically removed by the framework; unless the return value is
   * {@link DeleteControl#noFinalizerRemoval()}, which indicates that the controller has determined
   * that the resource should not be deleted yet. This is usually a corner case, when a cleanup is
   * tried again eventually.
   *
   * <p>
   * It's important for implementations of this method to be idempotent, since it can be called
   * several times.
   *
   * @param resource the resource that is marked for deletion
   * @param context the context with which the operation is executed
   * @return {@link DeleteControl#defaultDelete()} - so the finalizer is automatically removed after
   *         the call. Use {@link DeleteControl#noFinalizerRemoval()} when you don't want to remove
   *         the finalizer immediately but rather wait asynchronously until all secondary resources
   *         are deleted, thus allowing you to keep the primary resource around until you are sure
   *         that it can be safely deleted.
   * @see DeleteControl#noFinalizerRemoval()
   */
  DeleteControl cleanup(P resource, Context<P> context) throws Exception;

}
