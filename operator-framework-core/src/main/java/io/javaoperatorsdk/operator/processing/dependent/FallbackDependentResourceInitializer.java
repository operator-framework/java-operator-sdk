package io.javaoperatorsdk.operator.processing.dependent;

import java.lang.reflect.InvocationTargetException;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigService;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceInitializer;

public class FallbackDependentResourceInitializer<T extends DependentResource<?, ?>, K extends DependentResourceConfigService>
    implements DependentResourceInitializer<T, K> {

  @Override
  public T initialize(
      Class<T> resourceClass,
      ControllerConfiguration<?> controllerConfiguration,
      KubernetesClient client) {
    try {
      return resourceClass.getConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
