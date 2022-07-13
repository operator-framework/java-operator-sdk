package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

@SuppressWarnings("rawtypes")
public class DefaultManagedWorkflow<P extends HasMetadata> implements ManagedWorkflow<P> {

  private final Workflow<P> workflow;
  private final boolean isCleaner;
  private final boolean isEmptyWorkflow;
  private final Map<String, DependentResource> dependentResourcesByName;

  DefaultManagedWorkflow(KubernetesClient client,
      List<DependentResourceSpec> dependentResourceSpecs,
      ManagedWorkflowSupport managedWorkflowSupport) {
    managedWorkflowSupport.checkForNameDuplication(dependentResourceSpecs);
    dependentResourcesByName = dependentResourceSpecs
        .stream().collect(Collectors.toMap(DependentResourceSpec::getName,
            spec -> managedWorkflowSupport.createAndConfigureFrom(spec, client)));

    isEmptyWorkflow = dependentResourceSpecs.isEmpty();
    workflow =
        managedWorkflowSupport.createWorkflow(dependentResourceSpecs, dependentResourcesByName);
    isCleaner = checkIfCleaner();
  }

  public WorkflowReconcileResult reconcile(P primary, Context<P> context) {
    return workflow.reconcile(primary, context);
  }

  public WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    return workflow.cleanup(primary, context);
  }

  private boolean checkIfCleaner() {
    for (var dr : workflow.getDependentResources()) {
      if (dr instanceof Deleter && !(dr instanceof GarbageCollected)) {
        return true;
      }
    }
    return false;
  }

  public boolean isCleaner() {
    return isCleaner;
  }

  public boolean isEmptyWorkflow() {
    return isEmptyWorkflow;
  }

  public Map<String, DependentResource> getDependentResourcesByName() {
    return dependentResourcesByName;
  }
}
