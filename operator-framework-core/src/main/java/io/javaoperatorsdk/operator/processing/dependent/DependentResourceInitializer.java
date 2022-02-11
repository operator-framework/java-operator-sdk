package io.javaoperatorsdk.operator.processing.dependent;

import java.lang.reflect.InvocationTargetException;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DependentResourceInitializer<T extends DependentResource<?, ?>, K extends DependentResourceConfiguration<?, ?>> {

  public T initDependentResource(K config, KubernetesClient kubernetesClient) {
    try {
      return (T) config.getDependentResourceClass().getConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
