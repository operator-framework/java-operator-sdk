package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

import static io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver.configure;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface DependentResourceFactory<C extends ControllerConfiguration<?>> {

  DependentResourceFactory DEFAULT = new DependentResourceFactory() {};

  default DependentResource createFrom(DependentResourceSpec spec, C configuration) {
    final var dependentResourceClass = spec.getDependentResourceClass();
    return Utils.instantiateAndConfigureIfNeeded(dependentResourceClass,
        DependentResource.class,
        Utils.contextFor(configuration, dependentResourceClass, Dependent.class),
        (instance) -> configure(instance, spec, configuration));
  }
}
