package io.javaoperatorsdk.operator.sample.workflowallfeature;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class PublicKeyConfigMap
    extends CRUDKubernetesDependentResource<ConfigMap, WorkflowAllFeatureCustomResource> {

  public PublicKeyConfigMap() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(WorkflowAllFeatureCustomResource primary,
      Context<WorkflowAllFeatureCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder().withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace()).build());
    var relatedSecret = context.getSecondaryResource(Secret.class);
    configMap.setData(Map.of("publicKey",
        "Public --- " + relatedSecret.orElseThrow().getData().get("createdKey") + " ---"));
    return configMap;
  }
}
