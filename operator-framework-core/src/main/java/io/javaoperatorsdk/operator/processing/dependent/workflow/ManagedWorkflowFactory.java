package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public interface ManagedWorkflowFactory<C extends ControllerConfiguration<?>> {

  @SuppressWarnings({"rawtypes", "unchecked"})
  ManagedWorkflowFactory DEFAULT = (configuration) -> {
    final var dependentResourceSpecs = configuration.getDependentResources();
    if (dependentResourceSpecs == null || dependentResourceSpecs.isEmpty()) {
      return new ManagedWorkflow() {
        @Override
        public boolean hasCleaner() {
          return false;
        }

        @Override
        public boolean isEmpty() {
          return true;
        }

        @Override
        public Workflow resolve(KubernetesClient client, ControllerConfiguration configuration) {
          return new DefaultWorkflow(null);
        }
      };
    }
    return ManagedWorkflowSupport.instance().createWorkflow(dependentResourceSpecs);
  };

  @SuppressWarnings("rawtypes")
  ManagedWorkflow workflowFor(C configuration);
}

