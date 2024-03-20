package io.javaoperatorsdk.operator.sample.workflowactivationcondition;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import java.util.Map;

public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, WorkflowActivationConditionCustomResource> {

  public static final String DATA_KEY = "data";

  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(WorkflowActivationConditionCustomResource primary,
      Context<WorkflowActivationConditionCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    configMap.setData(Map.of(DATA_KEY, primary.getSpec().getValue()));
    return configMap;
  }
}
