package io.javaoperatorsdk.operator.sample.workflowexplicitinvocation;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import java.util.Map;

public class ConfigMapDependent extends
    CRUDNoGCKubernetesDependentResource<ConfigMap, WorkflowExplicitInvocationCustomResource> {

  public ConfigMapDependent() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(WorkflowExplicitInvocationCustomResource primary,
      Context<WorkflowExplicitInvocationCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build())
        .withData(Map.of("key", primary.getSpec().getValue()))
        .build();
  }
}
