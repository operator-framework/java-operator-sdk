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
  private boolean isCleaner;
  private final boolean isEmptyWorkflow;
  private final Map<String, DependentResource> dependentResourcesByName;
  private boolean resolved;
  private final ManagedWorkflowSupport managedWorkflowSupport;

  DefaultManagedWorkflow(List<DependentResourceSpec> dependentResourceSpecs, Workflow<P> workflow,
      ManagedWorkflowSupport managedWorkflowSupport) {
    dependentResourcesByName = new HashMap<>(dependentResourceSpecs.size());
    isEmptyWorkflow = dependentResourceSpecs.isEmpty();
    this.workflow = workflow;
    this.managedWorkflowSupport = managedWorkflowSupport;
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
    checkIfResolved();
    return isCleaner;
  }

  public boolean isEmptyWorkflow() {
    return isEmptyWorkflow;
  }

  public Map<String, DependentResource> getDependentResourcesByName() {
    checkIfResolved();
    return dependentResourcesByName;
  }

  @Override
  public ManagedWorkflow<P> resolve(KubernetesClient client, List<DependentResourceSpec> specs) {
    final boolean[] cleanerHolder = {false};
    specs.forEach(spec -> {
      final var dr = managedWorkflowSupport.createAndConfigureFrom(spec, client);
      dependentResourcesByName.put(spec.getName(), dr);
      if (DependentResource.canDeleteIfAble(dr)) {
        cleanerHolder[0] = true;
      }
    });

    workflow.resolve(client, specs);
    isCleaner = cleanerHolder[0];
    resolved = true;
    return this;
  }

  private void checkIfResolved() {
    if (!resolved) {
      throw new IllegalStateException("resolve should be called before");
    }
  }
}
