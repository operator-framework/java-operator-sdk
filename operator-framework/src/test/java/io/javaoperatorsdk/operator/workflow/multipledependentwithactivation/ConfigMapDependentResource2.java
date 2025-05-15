package io.javaoperatorsdk.operator.workflow.multipledependentwithactivation;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(informer = @Informer(name = "configMapInformer"))
public class ConfigMapDependentResource2
    extends CRUDNoGCKubernetesDependentResource<
        ConfigMap, MultipleDependentActivationCustomResource> {

  public static final String DATA_KEY = "data";
  public static final String SUFFIX = "2";

  @Override
  protected ConfigMap desired(
      MultipleDependentActivationCustomResource primary,
      Context<MultipleDependentActivationCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName() + SUFFIX)
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    configMap.setData(Map.of(DATA_KEY, primary.getSpec().getValue() + SUFFIX));
    return configMap;
  }
}
