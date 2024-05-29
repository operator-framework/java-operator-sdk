package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface DependentResourceFactory<C extends ControllerConfiguration<?>> {

  DependentResourceFactory DEFAULT = new DependentResourceFactory() {};

  default DependentResource createFrom(DependentResourceSpec spec, C configuration) {
    final var dependentResourceClass = spec.getDependentResourceClass();
    return Utils.instantiateAndConfigureIfNeeded(dependentResourceClass,
        DependentResource.class,
        Utils.contextFor(configuration, dependentResourceClass, Dependent.class),
        (instance) -> {
          if (instance instanceof ConfiguredDependentResource configurable) {
            spec.getConfiguration().ifPresent(configurable::configureWith);
          }
        });
  }
}
