package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigService;

public interface DependentResourceInitializer<T extends DependentResource<?, ?>, K extends DependentResourceConfigService> {

  default void useConfigService(K configService) {}

  T initialize(Class<T> resourceClass, ControllerConfiguration<?> controllerConfiguration,
      KubernetesClient client);

}
