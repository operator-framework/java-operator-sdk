package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public interface DependentResourceInitializer<T extends DependentResource> {

  T initialize(Class<T> resourceClass, ControllerConfiguration<?> controllerConfiguration,
      KubernetesClient client);

}
