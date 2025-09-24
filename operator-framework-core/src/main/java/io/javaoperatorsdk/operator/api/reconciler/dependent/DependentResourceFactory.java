package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface DependentResourceFactory<
    C extends ControllerConfiguration<?>, D extends DependentResourceSpec> {

  DependentResourceFactory DEFAULT = new DependentResourceFactory() {};

  default DependentResource createFrom(D spec, C controllerConfiguration) {
    final var dependentResourceClass = spec.getDependentResourceClass();
    return Utils.instantiateAndConfigureIfNeeded(
        dependentResourceClass,
        DependentResource.class,
        Utils.contextFor(controllerConfiguration, dependentResourceClass, Dependent.class),
        (instance) -> configure(instance, spec, controllerConfiguration));
  }

  default void configure(DependentResource instance, D spec, C controllerConfiguration) {
    if (instance instanceof ConfiguredDependentResource configurable) {
      final var config = controllerConfiguration.getConfigurationFor(spec);
      if (config != null) {
        configurable.configureWith(config);
      }
    }
  }

  default Class<?> associatedResourceType(D spec) {
    final var dependentResourceClass = spec.getDependentResourceClass();
    final var dr =
        Utils.instantiateAndConfigureIfNeeded(
            dependentResourceClass, DependentResource.class, null, null);
    return dr != null ? dr.resourceType() : null;
  }

  default DependentResourceNode createNodeFrom(D spec, DependentResource dependentResource) {
    return new DependentResourceNode(
        spec.getReconcileCondition(),
        spec.getDeletePostCondition(),
        spec.getReadyCondition(),
        spec.getActivationCondition(),
        dependentResource);
  }
}
