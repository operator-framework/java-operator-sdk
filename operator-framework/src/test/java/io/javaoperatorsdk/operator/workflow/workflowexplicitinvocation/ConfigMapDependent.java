package io.javaoperatorsdk.operator.workflow.workflowexplicitinvocation;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class ConfigMapDependent
    extends CRUDNoGCKubernetesDependentResource<
        ConfigMap, WorkflowExplicitInvocationCustomResource> {

  @Override
  protected ConfigMap desired(
      WorkflowExplicitInvocationCustomResource primary,
      Context<WorkflowExplicitInvocationCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(primary.getMetadata().getName())
                .withNamespace(primary.getMetadata().getNamespace())
                .build())
        .withData(Map.of("key", primary.getSpec().getValue()))
        .build();
  }
}
