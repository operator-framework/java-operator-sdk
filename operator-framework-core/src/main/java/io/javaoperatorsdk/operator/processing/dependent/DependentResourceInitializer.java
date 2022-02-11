package io.javaoperatorsdk.operator.processing.dependent;

import java.lang.reflect.InvocationTargetException;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;

public class DependentResourceInitializer<T extends DependentResource<?, ?>, K extends DependentResourceConfiguration<?, ?>> {

  public T initDependentResource(K config, KubernetesClient kubernetesClient) {
    try {
      DependentResource dependentResource =
          config.getDependentResourceClass().getConstructor().newInstance();
      if (dependentResource instanceof KubernetesClientAware) {
        ((KubernetesClientAware) dependentResource).setKubernetesClient(kubernetesClient);
      }
      return (T) dependentResource;
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
