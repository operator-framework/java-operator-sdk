package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow.noOpWorkflow;

public interface ManagedWorkflowFactory {

  ManagedWorkflowFactory DEFAULT = (configuration, managedWorkflowSupport) -> {
    final var dependentResourceSpecs = configuration.getDependentResources();
    if (dependentResourceSpecs == null || dependentResourceSpecs.isEmpty()) {
      return noOpWorkflow;
    }
    return new DefaultManagedWorkflow(dependentResourceSpecs,
        managedWorkflowSupport.createWorkflow(dependentResourceSpecs), managedWorkflowSupport);
  };

  @SuppressWarnings("rawtypes")
  default ManagedWorkflow workflowFor(ControllerConfiguration configuration) {
    return workflowFor(configuration, ManagedWorkflowSupport.instance());
  }

  @SuppressWarnings("rawtypes")
  ManagedWorkflow workflowFor(ControllerConfiguration configuration,
      ManagedWorkflowSupport managedWorkflowSupport);
}

