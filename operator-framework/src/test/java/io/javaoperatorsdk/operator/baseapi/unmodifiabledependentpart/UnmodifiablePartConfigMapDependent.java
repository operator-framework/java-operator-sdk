package io.javaoperatorsdk.operator.baseapi.unmodifiabledependentpart;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class UnmodifiablePartConfigMapDependent
    extends CRUDKubernetesDependentResource<ConfigMap, UnmodifiableDependentPartCustomResource> {

  public static final String UNMODIFIABLE_INITIAL_DATA_KEY = "initialDataKey";
  public static final String ACTUAL_DATA_KEY = "actualDataKey";

  @Override
  protected ConfigMap desired(
      UnmodifiableDependentPartCustomResource primary,
      Context<UnmodifiableDependentPartCustomResource> context) {
    var actual = context.getSecondaryResource(ConfigMap.class);
    ConfigMap res =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(primary.getMetadata().getName())
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .build();
    res.setData(
        Map.of(
            ACTUAL_DATA_KEY,
            primary.getSpec().getData(),
            // setting the old data if available
            UNMODIFIABLE_INITIAL_DATA_KEY,
            actual
                .map(cm -> cm.getData().get(UNMODIFIABLE_INITIAL_DATA_KEY))
                .orElse(primary.getSpec().getData())));
    return res;
  }
}
