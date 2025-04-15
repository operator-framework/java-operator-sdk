package io.javaoperatorsdk.operator.dependent.primarytosecondaydependent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class ConfigMapDependent
    extends KubernetesDependentResource<ConfigMap, PrimaryToSecondaryDependentCustomResource> {

  public static final String TEST_CONFIG_MAP_NAME = "testconfigmap";

  @Override
  protected ConfigMap desired(
      PrimaryToSecondaryDependentCustomResource primary,
      Context<PrimaryToSecondaryDependentCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(TEST_CONFIG_MAP_NAME)
                .withNamespace(primary.getMetadata().getNamespace())
                .build())
        .build();
  }
}
