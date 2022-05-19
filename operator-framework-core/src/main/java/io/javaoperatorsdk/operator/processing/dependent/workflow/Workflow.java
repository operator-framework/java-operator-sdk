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
@SuppressWarnings("rawtypes")
public class Workflow<P extends HasMetadata> {

  private final Set<DependentResourceNode> dependentResourceNodes;
  private final Set<DependentResourceNode> topLevelResources = new HashSet<>();
  private final Set<DependentResourceNode> bottomLevelResource = new HashSet<>();
  private Map<DependentResourceNode, List<DependentResourceNode>> dependents;

  // it's "global" executor service shared between multiple reconciliations running parallel
  private ExecutorService executorService;

  public Workflow(Set<DependentResourceNode> dependentResourceNodes) {
    this.executorService = ConfigurationServiceProvider.instance().getExecutorService();
    this.dependentResourceNodes = dependentResourceNodes;
    preprocessForReconcile();
  }

  public Workflow(Set<DependentResourceNode> dependentResourceNodes,
      ExecutorService executorService) {
    this.executorService = executorService;
    this.dependentResourceNodes = dependentResourceNodes;
    preprocessForReconcile();
  }

  public Workflow(Set<DependentResourceNode> dependentResourceNodes, int globalParallelism) {
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
        for (DependentResourceNode dependsOn : node.getDependsOn()) {
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

  Set<DependentResourceNode> getTopLevelDependentResources() {
    return topLevelResources;
  }

  Set<DependentResourceNode> getBottomLevelResource() {
    return bottomLevelResource;
  }

  @SuppressWarnings("rawtypes")
  List<DependentResourceNode> getDependents(DependentResourceNode node) {
    var deps = dependents.get(node);
    if (deps == null) {
      return Collections.emptyList();
    } else {
      return deps;
    }
  }

  Map<DependentResourceNode, List<DependentResourceNode>> getDependents() {
    return dependents;
  }

  ExecutorService getExecutorService() {
    return executorService;
  }
}
