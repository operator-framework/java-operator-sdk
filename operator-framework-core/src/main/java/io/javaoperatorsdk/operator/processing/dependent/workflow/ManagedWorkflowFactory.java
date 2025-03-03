package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;

public interface ManagedWorkflowFactory<C extends ControllerConfiguration<?>> {

  @SuppressWarnings({"rawtypes", "unchecked"})
  ManagedWorkflowFactory DEFAULT =
      (configuration) -> {
        final Optional<WorkflowSpec> workflowSpec = configuration.getWorkflowSpec();
        if (workflowSpec.isEmpty()) {
          return (ManagedWorkflow) (client, configuration1) -> new DefaultWorkflow(null);
        }
        ManagedWorkflowSupport support = new ManagedWorkflowSupport();
        return support.createWorkflow(workflowSpec.orElseThrow());
      };

  @SuppressWarnings("rawtypes")
  ManagedWorkflow workflowFor(C configuration);
}
