package io.javaoperatorsdk.operator.sample.workflowexplicitcleanup;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import java.util.Map;

public class ConfigMapDependent extends
    CRUDNoGCKubernetesDependentResource<ConfigMap, WorkflowExplicitCleanupCustomResource> {

  public ConfigMapDependent() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(WorkflowExplicitCleanupCustomResource primary,
      Context<WorkflowExplicitCleanupCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build())
        .withData(Map.of("key", "val"))
        .build();
  }
}
