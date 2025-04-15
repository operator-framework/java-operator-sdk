package io.javaoperatorsdk.operator.workflow.workflowsilentexceptionhandling;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class ConfigMapDependent
    extends CRUDNoGCKubernetesDependentResource<
        ConfigMap, HandleWorkflowExceptionsInReconcilerCustomResource> {

  @Override
  public ReconcileResult<ConfigMap> reconcile(
      HandleWorkflowExceptionsInReconcilerCustomResource primary,
      Context<HandleWorkflowExceptionsInReconcilerCustomResource> context) {
    throw new RuntimeException("Exception thrown on purpose");
  }

  @Override
  public void delete(
      HandleWorkflowExceptionsInReconcilerCustomResource primary,
      Context<HandleWorkflowExceptionsInReconcilerCustomResource> context) {
    throw new RuntimeException("Exception thrown on purpose");
  }
}
