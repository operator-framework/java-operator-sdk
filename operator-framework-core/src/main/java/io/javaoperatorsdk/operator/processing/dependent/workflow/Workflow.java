package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

/**
 * Dependents definition: so if B depends on A, the B is dependent of A.
 *
 * @param <P> primary resource
 */
@SuppressWarnings("rawtypes")
public class Workflow<P extends HasMetadata> {

  public static final boolean THROW_EXCEPTION_AUTOMATICALLY_DEFAULT = true;

  private final Set<DependentResourceNode> dependentResourceNodes;
  private final Set<DependentResourceNode> topLevelResources = new HashSet<>();
  private final Set<DependentResourceNode> bottomLevelResource = new HashSet<>();

  private final boolean throwExceptionAutomatically;
  // it's "global" executor service shared between multiple reconciliations running parallel
  private ExecutorService executorService;

  public Workflow(Set<DependentResourceNode> dependentResourceNodes) {
    this.executorService = ConfigurationServiceProvider.instance().getExecutorService();
    this.dependentResourceNodes = dependentResourceNodes;
    this.throwExceptionAutomatically = THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;
    preprocessForReconcile();
  }

  public Workflow(Set<DependentResourceNode> dependentResourceNodes,
      ExecutorService executorService, boolean throwExceptionAutomatically) {
    this.executorService = executorService;
    this.dependentResourceNodes = dependentResourceNodes;
    this.throwExceptionAutomatically = throwExceptionAutomatically;
    preprocessForReconcile();
  }

  public Workflow(Set<DependentResourceNode> dependentResourceNodes, int globalParallelism) {
    this(dependentResourceNodes, Executors.newFixedThreadPool(globalParallelism), true);
  }

  public WorkflowReconcileResult reconcile(P primary, Context<P> context) {
    WorkflowReconcileExecutor<P> workflowReconcileExecutor =
        new WorkflowReconcileExecutor<>(this, primary, context);
    var result = workflowReconcileExecutor.reconcile();
    if (throwExceptionAutomatically) {
      result.throwAggregateExceptionIfErrorsPresent();
    }
    return result;
  }

  public WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    WorkflowCleanupExecutor<P> workflowCleanupExecutor =
        new WorkflowCleanupExecutor<>(this, primary, context);
    var result = workflowCleanupExecutor.cleanup();
    if (throwExceptionAutomatically) {
      result.throwAggregateExceptionIfErrorsPresent();
    }
    return result;
  }

  // add cycle detection?
  private void preprocessForReconcile() {
    bottomLevelResource.addAll(dependentResourceNodes);
    for (DependentResourceNode<?, P> node : dependentResourceNodes) {
      if (node.getDependsOn().isEmpty()) {
        topLevelResources.add(node);
      } else {
        for (DependentResourceNode dependsOn : node.getDependsOn()) {
          bottomLevelResource.remove(dependsOn);
        }
      }
    }
  }

  public boolean isThrowExceptionAutomatically() {
    return throwExceptionAutomatically;
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

  ExecutorService getExecutorService() {
    return executorService;
  }

  public Set<DependentResource> getDependentResources() {
    return dependentResourceNodes.stream().map(DependentResourceNode::getDependentResource)
        .collect(Collectors.toSet());
  }
}
