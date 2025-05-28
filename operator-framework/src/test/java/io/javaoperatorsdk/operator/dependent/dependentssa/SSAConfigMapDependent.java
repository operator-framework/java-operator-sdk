package io.javaoperatorsdk.operator.dependent.dependentssa;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class SSAConfigMapDependent
    extends CRUDKubernetesDependentResource<ConfigMap, DependentSSACustomResource> {

  public static AtomicInteger NUMBER_OF_UPDATES = new AtomicInteger(0);

  public static final String DATA_KEY = "key1";

  @Override
  protected ConfigMap desired(
      DependentSSACustomResource primary, Context<DependentSSACustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(primary.getMetadata().getName())
                .withNamespace(primary.getMetadata().getNamespace())
                .build())
        .withData(Map.of(DATA_KEY, primary.getSpec().getValue()))
        .build();
  }

  @Override
  public ConfigMap update(
      ConfigMap actual,
      ConfigMap desired,
      DependentSSACustomResource primary,
      Context<DependentSSACustomResource> context) {
    NUMBER_OF_UPDATES.incrementAndGet();
    return super.update(actual, desired, primary, context);
  }
}
