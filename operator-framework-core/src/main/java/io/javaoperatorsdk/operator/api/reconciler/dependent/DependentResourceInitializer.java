package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigService;

/**
 * <p>
 * Should initialize the target DependentResource. Recognizing its annotations in case.
 * </p>
 * <p>
 * ConfigService is used to give possibility to override the default values, so it's not mandatory.
 * If config service is not set, the initializer should work just using default values or parsing values from
 * custom annotations.
 * </p>
 * @param <T> The dependent resource to create
 * @param <K> Use the config service if provided.
 */
public interface DependentResourceInitializer<T extends DependentResource<?, ?>, K extends DependentResourceConfigService> {

  default void useConfigService(K configService) {}

  T initialize(Class<T> resourceClass, ControllerConfiguration<?> controllerConfiguration,
      KubernetesClient client);

}
