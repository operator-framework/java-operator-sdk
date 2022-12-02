package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow.noOpWorkflow;

public interface ManagedWorkflowFactory {

  @SuppressWarnings({"rawtypes", "unchecked"})
  ManagedWorkflowFactory DEFAULT = (configuration) -> {
    final var dependentResourceSpecs = configuration.getDependentResources();
    if (dependentResourceSpecs == null || dependentResourceSpecs.isEmpty()) {
      return noOpWorkflow;
    }
    return new DefaultManagedWorkflow(dependentResourceSpecs,
        ManagedWorkflowSupport.instance().createWorkflow(dependentResourceSpecs));
  };

  @SuppressWarnings("rawtypes")
  ManagedWorkflow workflowFor(ControllerConfiguration configuration);
}

