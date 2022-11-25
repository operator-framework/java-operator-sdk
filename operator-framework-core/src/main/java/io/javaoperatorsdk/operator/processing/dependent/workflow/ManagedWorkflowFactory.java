package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow.noOpWorkflow;

public interface ManagedWorkflowFactory {

  ManagedWorkflowFactory DEFAULT = (client, dependentResourceSpecs) -> {
    if (dependentResourceSpecs == null || dependentResourceSpecs.isEmpty()) {
      return noOpWorkflow;
    }
    return new DefaultManagedWorkflow(client, dependentResourceSpecs,
        ManagedWorkflowSupport.instance());
  };


  @SuppressWarnings("rawtypes")
  ManagedWorkflow workflowFor(KubernetesClient client,
      List<DependentResourceSpec> dependentResourceSpecs);
}

