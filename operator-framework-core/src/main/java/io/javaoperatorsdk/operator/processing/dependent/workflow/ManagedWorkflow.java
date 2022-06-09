package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
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
  };

  @SuppressWarnings("unchecked")
  static ManagedWorkflow workflowFor(KubernetesClient client,
      List<DependentResourceSpec> dependentResourceSpecs) {
    if (dependentResourceSpecs == null || dependentResourceSpecs.isEmpty()) {
      return noOpWorkflow;
    }
    return new DefaultManagedWorkflow(client, dependentResourceSpecs,
        ManagedWorkflowSupport.instance());
  }

  WorkflowReconcileResult reconcile(P primary, Context<P> context);

  WorkflowCleanupResult cleanup(P primary, Context<P> context);

  boolean isCleaner();

  boolean isEmptyWorkflow();

  Map<String, DependentResource> getDependentResourcesByName();
}
