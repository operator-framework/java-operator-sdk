package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
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

  private final Set<DefaultDependentResourceNode> dependentResourceNodes;
  private final Set<DefaultDependentResourceNode> topLevelResources = new HashSet<>();
  private final Set<DefaultDependentResourceNode> bottomLevelResource = new HashSet<>();

  private final boolean throwExceptionAutomatically;
  // it's "global" executor service shared between multiple reconciliations running parallel
  private ExecutorService executorService;

  public Workflow(Set<DefaultDependentResourceNode> dependentResourceNodes) {
    this.executorService = ExecutorServiceManager.instance().workflowExecutorService();
    this.dependentResourceNodes = dependentResourceNodes;
    this.throwExceptionAutomatically = THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;
    preprocessForReconcile();
  }

  public Workflow(Set<DefaultDependentResourceNode> dependentResourceNodes,
      ExecutorService executorService, boolean throwExceptionAutomatically) {
    this.executorService = executorService;
    this.dependentResourceNodes = dependentResourceNodes;
    this.throwExceptionAutomatically = throwExceptionAutomatically;
    preprocessForReconcile();
  }

  public Workflow(Set<DefaultDependentResourceNode> dependentResourceNodes, int globalParallelism) {
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
  @SuppressWarnings("unchecked")
  private void preprocessForReconcile() {
    bottomLevelResource.addAll(dependentResourceNodes);
    for (DefaultDependentResourceNode<?, P> node : dependentResourceNodes) {
      if (node.getDependsOn().isEmpty()) {
        topLevelResources.add(node);
      } else {
        for (DefaultDependentResourceNode dependsOn : node.getDependsOn()) {
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

  Set<DefaultDependentResourceNode> getTopLevelDependentResources() {
    return topLevelResources;
  }

  Set<DefaultDependentResourceNode> getBottomLevelResource() {
    return bottomLevelResource;
  }

  ExecutorService getExecutorService() {
    return executorService;
  }

  public Set<DependentResource> getDependentResources() {
    return dependentResourceNodes.stream().map(DefaultDependentResourceNode::getDependentResource)
        .collect(Collectors.toSet());
  }
}
