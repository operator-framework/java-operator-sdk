package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public interface ManagedWorkflow<P extends HasMetadata> {

  ManagedWorkflow noOpWorkflow = new ManagedWorkflow() {
    @Override
    public WorkflowReconcileResult reconcile(HasMetadata primary, Context context) {
      throw new IllegalStateException("Shouldn't be called");
    }

    @Override
    public WorkflowCleanupResult cleanup(HasMetadata primary, Context context) {
      throw new IllegalStateException("Shouldn't be called");
    }

    @Override
    public boolean isCleaner() {
      return false;
    }

    @Override
    public boolean isEmptyWorkflow() {
      return true;
    }

    @Override
    public Map<String, DependentResource> getDependentResourcesByName() {
      return Collections.emptyMap();
    }

    @Override
    public ManagedWorkflow resolve(KubernetesClient client, ControllerConfiguration configuration) {
      return this;
    }
  };

  WorkflowReconcileResult reconcile(P primary, Context<P> context);

  WorkflowCleanupResult cleanup(P primary, Context<P> context);

  boolean isCleaner();

  boolean isEmptyWorkflow();

  Map<String, DependentResource> getDependentResourcesByName();

  ManagedWorkflow<P> resolve(KubernetesClient client, ControllerConfiguration<P> configuration);
}
