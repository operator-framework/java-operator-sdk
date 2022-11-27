package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
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

  private final Map<String, DependentResourceNode> dependentResourceNodes;
  private final Set<DependentResourceNode> topLevelResources = new HashSet<>();
  private final Set<DependentResourceNode> bottomLevelResource = new HashSet<>();

  private final boolean throwExceptionAutomatically;
  // it's "global" executor service shared between multiple reconciliations running parallel
  private final ExecutorService executorService;
  private boolean resolved;

  Workflow(Set<DependentResourceNode> dependentResourceNodes) {
    this(dependentResourceNodes, ExecutorServiceManager.instance().workflowExecutorService(),
        THROW_EXCEPTION_AUTOMATICALLY_DEFAULT, false);
  }

  Workflow(Set<DependentResourceNode> dependentResourceNodes,
      ExecutorService executorService, boolean throwExceptionAutomatically, boolean resolved) {
    this.executorService = executorService;
    this.dependentResourceNodes = dependentResourceNodes.stream()
        .collect(Collectors.toMap(DependentResourceNode::getName, Function.identity()));
    this.throwExceptionAutomatically = throwExceptionAutomatically;
    this.resolved = resolved;
    preprocessForReconcile();
  }

  public DependentResource getDependentResourceFor(DependentResourceNode node) {
    throwIfUnresolved();
    return ((AbstractDependentResourceNode) dependentResourceNodes.get(node.getName()))
        .getDependentResource();
  }

  private void throwIfUnresolved() {
    if (!resolved) {
      throw new IllegalStateException(
          "Should call resolved before trying to access DependentResources");
    }
  }

  public WorkflowReconcileResult reconcile(P primary, Context<P> context) {
    throwIfUnresolved();
    WorkflowReconcileExecutor<P> workflowReconcileExecutor =
        new WorkflowReconcileExecutor<>(this, primary, context);
    var result = workflowReconcileExecutor.reconcile();
    if (throwExceptionAutomatically) {
      result.throwAggregateExceptionIfErrorsPresent();
    }
    return result;
  }

  public WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    throwIfUnresolved();
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
    final var nodes = new ArrayList<>(dependentResourceNodes.values());
    bottomLevelResource.addAll(nodes);
    for (DependentResourceNode<?, P> node : nodes) {
      if (node.getDependsOn().isEmpty()) {
        topLevelResources.add(node);
      } else {
        for (DependentResourceNode dependsOn : node.getDependsOn()) {
          bottomLevelResource.remove(dependsOn);
        }
      }
    }
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

  Set<DependentResourceNode> nodes() {
    return new HashSet<>(dependentResourceNodes.values());
  }

  @SuppressWarnings("unchecked")
  public void resolve(KubernetesClient client, List<DependentResourceSpec> dependentResources) {
    if (!resolved) {
      dependentResourceNodes.values().forEach(drn -> drn.resolve(client, dependentResources));
      resolved = true;
    }
  }
}
