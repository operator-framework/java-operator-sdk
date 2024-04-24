package io.javaoperatorsdk.operator.sample.workflowsilentexceptionhandling;


import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class ConfigMapDependent extends
    CRUDNoGCKubernetesDependentResource<ConfigMap, WorkflowSilentExceptionHandlingCustomResource> {

  public ConfigMapDependent() {
    super(ConfigMap.class);
  }

  @Override
  public ReconcileResult<ConfigMap> reconcile(WorkflowSilentExceptionHandlingCustomResource primary,
      Context<WorkflowSilentExceptionHandlingCustomResource> context) {
    throw new RuntimeException("Exception thrown on purpose");
  }

  @Override
  public void delete(WorkflowSilentExceptionHandlingCustomResource primary,
      Context<WorkflowSilentExceptionHandlingCustomResource> context) {
    throw new RuntimeException("Exception thrown on purpose");
  }
}
