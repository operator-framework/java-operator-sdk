package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * Dependents definition: so if B depends on A, the B is dependent of A.
 *
 * @param <P> primary resource
 */
public class Workflow<P extends HasMetadata> {

  private final Set<DependentResourceNode<?, P>> dependentResourceNodes;
  private final Set<DependentResourceNode<?, P>> topLevelResources = new HashSet<>();
  private final Set<DependentResourceNode<?, P>> bottomLevelResource = new HashSet<>();
  private Map<DependentResourceNode<?, P>, List<DependentResourceNode<?, P>>> dependents;

  // it's "global" executor service shared between multiple reconciliations running parallel
  private ExecutorService executorService;

  public Workflow(Set<DependentResourceNode<?, P>> dependentResourceNodes) {
    this.executorService = ConfigurationServiceProvider.instance().getExecutorService();
    this.dependentResourceNodes = dependentResourceNodes;
    preprocessForReconcile();
  }

  public Workflow(Set<DependentResourceNode<?, P>> dependentResourceNodes,
      ExecutorService executorService) {
    this.executorService = executorService;
    this.dependentResourceNodes = dependentResourceNodes;
    preprocessForReconcile();
  }

  public Workflow(Set<DependentResourceNode<?, P>> dependentResourceNodes, int globalParallelism) {
    this(dependentResourceNodes, Executors.newFixedThreadPool(globalParallelism));
  }

  public WorkflowExecutionResult reconcile(P primary, Context<P> context) {
    WorkflowReconcileExecutor<P> workflowReconcileExecutor =
        new WorkflowReconcileExecutor<>(this, primary, context);
    return workflowReconcileExecutor.reconcile();
  }

  public WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    WorkflowCleanupExecutor<P> workflowCleanupExecutor =
        new WorkflowCleanupExecutor<>(this, primary, context);
    return workflowCleanupExecutor.cleanup();
  }

  // add cycle detection?
  private void preprocessForReconcile() {
    bottomLevelResource.addAll(dependentResourceNodes);
    dependents = new ConcurrentHashMap<>(dependentResourceNodes.size());
    for (DependentResourceNode<?, P> node : dependentResourceNodes) {
      if (node.getDependsOn().isEmpty()) {
        topLevelResources.add(node);
      } else {
        for (DependentResourceNode<?, P> dependsOn : node.getDependsOn()) {
          dependents.computeIfAbsent(dependsOn, dr -> new ArrayList<>());
          dependents.get(dependsOn).add(node);
          bottomLevelResource.remove(dependsOn);
        }
      }
    }
  }

  public void setExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
  }

  Set<DependentResourceNode<?, P>> getTopLevelDependentResources() {
    return topLevelResources;
  }

  Set<DependentResourceNode<?, P>> getBottomLevelResource() {
    return bottomLevelResource;
  }

  @SuppressWarnings("rawtypes")
  List<DependentResourceNode<?, P>> getDependents(DependentResourceNode node) {
    var deps = dependents.get(node);
    if (deps == null) {
      return Collections.emptyList();
    } else {
      return deps;
    }
  }

  Map<DependentResourceNode<?, P>, List<DependentResourceNode<?, P>>> getDependents() {
    return dependents;
  }

  ExecutorService getExecutorService() {
    return executorService;
  }
}
