package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class DefaultManagedWorkflow<P extends HasMetadata> implements ManagedWorkflow<P> {

  private final Workflow<P> workflow;
  private final boolean isEmptyWorkflow;
  private boolean resolved;

  DefaultManagedWorkflow(List<DependentResourceSpec> dependentResourceSpecs, Workflow<P> workflow) {
    isEmptyWorkflow = dependentResourceSpecs.isEmpty();
    this.workflow = workflow;
  }

  public WorkflowReconcileResult reconcile(P primary, Context<P> context) {
    checkIfResolved();
    return workflow.reconcile(primary, context);
  }

  public WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    checkIfResolved();
    return workflow.cleanup(primary, context);
  }

  public boolean isCleaner() {
    return workflow.hasCleaner();
  }

  public boolean isEmptyWorkflow() {
    return isEmptyWorkflow;
  }

  public Map<String, DependentResource> getDependentResourcesByName() {
    checkIfResolved();
    final var nodes = workflow.nodes();
    final var result = new HashMap<String, DependentResource>(nodes.size());
    nodes.forEach((key, drn) -> result.put(key, workflow.getDependentResourceFor(drn)));
    return result;
  }

  @Override
  public ManagedWorkflow<P> resolve(KubernetesClient client, List<DependentResourceSpec> specs) {
    if (!resolved) {
      workflow.resolve(client, specs);
      resolved = true;
    }
    return this;
  }

  private void checkIfResolved() {
    if (!resolved) {
      throw new IllegalStateException("resolve should be called before");
    }
  }
}
