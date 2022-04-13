package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  private final List<DependentResourceNode<?, ?>> dependentResourceNodes;
  private final List<DependentResourceNode<?, ?>> topLevelResources = new ArrayList<>();
  private Map<DependentResourceNode<?, ?>, List<DependentResourceNode<?, ?>>> dependents;

  // it's "global" executor service shared between multiple reconciliations running parallel
  private ExecutorService executorService;

  public Workflow(List<DependentResourceNode<?, ?>> dependentResourceNodes) {
    this.executorService = ConfigurationServiceProvider.instance().getExecutorService();
    this.dependentResourceNodes = dependentResourceNodes;
    preprocessForReconcile();
  }

  public Workflow(List<DependentResourceNode<?, ?>> dependentResourceNodes,
      ExecutorService executorService) {
    this.executorService = executorService;
    this.dependentResourceNodes = dependentResourceNodes;
    preprocessForReconcile();
  }

  public Workflow(List<DependentResourceNode<?, ?>> dependentResourceNodes, int globalParallelism) {
    this(dependentResourceNodes, Executors.newFixedThreadPool(globalParallelism));
  }

  public void reconcile(P primary, Context<P> context) {
    WorkflowReconcileExecutor<P> workflowReconcileExecutor =
        new WorkflowReconcileExecutor<>(this, primary, context);
    workflowReconcileExecutor.reconcile();
  }

  public void cleanup(P resource, Context<P> context) {

  }

  // add cycle detection?
  private void preprocessForReconcile() {
    dependents = new ConcurrentHashMap<>(dependentResourceNodes.size());
    for (DependentResourceNode<?, ?> node : dependentResourceNodes) {
      if (node.getDependsOn().isEmpty()) {
        topLevelResources.add(node);
      } else {
        for (DependentResourceNode<?, ?> dependsOn : node.getDependsOn()) {
          dependents.computeIfAbsent(dependsOn, dr -> new ArrayList<>());
          dependents.get(dependsOn).add(node);
        }
      }
    }
  }

  public void setExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
  }

  List<DependentResourceNode<?, ?>> getTopLevelDependentResources() {
    return topLevelResources;
  }

  Map<DependentResourceNode<?, ?>, List<DependentResourceNode<?, ?>>> getDependents() {
    return dependents;
  }

  ExecutorService getExecutorService() {
    return executorService;
  }
}
