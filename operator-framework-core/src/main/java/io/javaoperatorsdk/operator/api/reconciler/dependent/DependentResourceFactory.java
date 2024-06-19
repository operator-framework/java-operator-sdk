package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface DependentResourceFactory<C extends ControllerConfiguration<?>> {

  DependentResourceFactory DEFAULT = new DependentResourceFactory() {};

  default DependentResource createFrom(DependentResourceSpec spec, C controllerConfiguration) {
    final var dependentResourceClass = spec.getDependentResourceClass();
    return Utils.instantiateAndConfigureIfNeeded(dependentResourceClass,
        DependentResource.class,
        Utils.contextFor(controllerConfiguration, dependentResourceClass, Dependent.class),
        (instance) -> configure(instance, spec, controllerConfiguration));
  }

  default void configure(DependentResource instance, DependentResourceSpec spec,
      C controllerConfiguration) {
    if (instance instanceof ConfiguredDependentResource configurable) {
      final var config = controllerConfiguration.getConfigurationFor(spec);
      if (config != null) {
        configurable.configureWith(config);
      }
    }
  }
}
