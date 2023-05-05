package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public interface ManagedWorkflowFactory<C extends ControllerConfiguration<?>> {

  @SuppressWarnings({"rawtypes", "unchecked"})
  ManagedWorkflowFactory DEFAULT = (configuration) -> {
    final var dependentResourceSpecs = configuration.getDependentResources();
    if (dependentResourceSpecs == null || dependentResourceSpecs.isEmpty()) {
      return (ManagedWorkflow) (client, configuration1) -> new DefaultWorkflow(null);
    }
    ManagedWorkflowSupport support = new ManagedWorkflowSupport();
    return support.createWorkflow(dependentResourceSpecs);
  };

  @SuppressWarnings("rawtypes")
  ManagedWorkflow workflowFor(C configuration);
}

