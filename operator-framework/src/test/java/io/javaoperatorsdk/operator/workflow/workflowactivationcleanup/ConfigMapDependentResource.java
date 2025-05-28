package io.javaoperatorsdk.operator.workflow.workflowactivationcleanup;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class ConfigMapDependentResource
    extends CRUDNoGCKubernetesDependentResource<
        ConfigMap, WorkflowActivationCleanupCustomResource> {

  public static final String DATA_KEY = "data";

  @Override
  protected ConfigMap desired(
      WorkflowActivationCleanupCustomResource primary,
      Context<WorkflowActivationCleanupCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    configMap.setData(Map.of(DATA_KEY, primary.getSpec().getValue()));
    return configMap;
  }
}
