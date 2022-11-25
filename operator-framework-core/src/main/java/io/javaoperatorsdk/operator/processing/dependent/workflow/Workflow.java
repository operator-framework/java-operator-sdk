package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
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

  private final Map<String, ResolvedNode> dependentResourceNodes;
  private final Set<DependentResourceNode> topLevelResources = new HashSet<>();
  private final Set<DependentResourceNode> bottomLevelResource = new HashSet<>();

  private final boolean throwExceptionAutomatically;
  // it's "global" executor service shared between multiple reconciliations running parallel
  private final ExecutorService executorService;

  public Workflow(Set<DependentResourceNode> dependentResourceNodes) {
    this(dependentResourceNodes, ExecutorServiceManager.instance().workflowExecutorService(),
        THROW_EXCEPTION_AUTOMATICALLY_DEFAULT);
  }

  public Workflow(Set<DependentResourceNode> dependentResourceNodes,
      ExecutorService executorService, boolean throwExceptionAutomatically) {
    this.executorService = executorService;
    this.dependentResourceNodes = dependentResourceNodes.stream()
        .collect(Collectors.toMap(DependentResourceNode::getName, ResolvedNode::new));
    this.throwExceptionAutomatically = throwExceptionAutomatically;
    preprocessForReconcile();
  }

  public DependentResource getDependentResourceFor(DependentResourceNode node) {
    return dependentResourceNodes.get(node.getName()).dependentResource();
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
    final var nodes = dependentResourceNodes.values().stream()
        .map(ResolvedNode::node).collect(Collectors.toList());
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
    return dependentResourceNodes.values().stream().map(ResolvedNode::node)
        .collect(Collectors.toSet());
  }

  public void resolve(KubernetesClient client, List<DependentResourceSpec> dependentResources) {
    dependentResourceNodes.values().forEach(drn -> drn.resolve(client, dependentResources));
  }

  private static class ResolvedNode {

    private final DependentResourceNode node;
    private DependentResource dependentResource;

    private ResolvedNode(DependentResourceNode node) {
      this.node = node;
      if (node instanceof DefaultDependentResourceNode) {
        this.dependentResource = ((DefaultDependentResourceNode) node).getDependentResource();
      }
    }

    public void setDependentResource(DependentResource dependentResource) {
      this.dependentResource = dependentResource;
    }

    public DependentResource dependentResource() {
      return dependentResource;
    }

    public DependentResourceNode node() {
      return node;
    }

    public void resolve(KubernetesClient client, List<DependentResourceSpec> dependentResources) {
      final var spec = dependentResources.stream()
          .filter(drs -> drs.getName().equals(node.getName()))
          .findFirst().orElseThrow();
      dependentResource = ManagedWorkflowSupport.instance().createAndConfigureFrom(spec, client);
    }
  }
}
