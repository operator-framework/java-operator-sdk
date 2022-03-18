package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Reconciler<R extends HasMetadata> {

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible.
   *
   * @param resource the resource that has been created or updated
   * @param context the context with which the operation is executed
   * @return UpdateControl to manage updates on the custom resource (usually the status) after
   *         reconciliation.
   */
  UpdateControl<R> reconcile(R resource, Context<R> context) throws Exception;

}
