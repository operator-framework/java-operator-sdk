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
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

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
  private final boolean hasCleaner;

  Workflow(Set<DependentResourceNode> dependentResourceNodes, boolean hasCleaner) {
    this(dependentResourceNodes, ExecutorServiceManager.instance().workflowExecutorService(),
        THROW_EXCEPTION_AUTOMATICALLY_DEFAULT, false, hasCleaner);
  }

  Workflow(Set<DependentResourceNode> dependentResourceNodes,
      ExecutorService executorService, boolean throwExceptionAutomatically, boolean resolved,
      boolean hasCleaner) {
    this.executorService = executorService;
    this.dependentResourceNodes = toMap(dependentResourceNodes);
    this.throwExceptionAutomatically = throwExceptionAutomatically;
    this.resolved = resolved;
    this.hasCleaner = hasCleaner;
  }

  private Map<String, DependentResourceNode> toMap(
      Set<DependentResourceNode> dependentResourceNodes) {
    final var nodes = new ArrayList<>(dependentResourceNodes);
    bottomLevelResource.addAll(nodes);
    return dependentResourceNodes.stream()
        .peek(drn -> {
          // add cycle detection?
          if (drn.getDependsOn().isEmpty()) {
            topLevelResources.add(drn);
          } else {
            for (DependentResourceNode dependsOn : (List<DependentResourceNode>) drn
                .getDependsOn()) {
              bottomLevelResource.remove(dependsOn);
            }
          }
        })
        .collect(Collectors.toMap(DependentResourceNode::getName, Function.identity()));
  }

  public DependentResource getDependentResourceFor(DependentResourceNode node) {
    throwIfUnresolved();
    return dependentResource(node);
  }

  private DependentResource dependentResource(DependentResourceNode node) {
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

  Set<DependentResourceNode> getTopLevelDependentResources() {
    return topLevelResources;
  }

  Set<DependentResourceNode> getBottomLevelResource() {
    return bottomLevelResource;
  }

  ExecutorService getExecutorService() {
    return executorService;
  }

  Map<String, DependentResourceNode> nodes() {
    return dependentResourceNodes;
  }

  @SuppressWarnings("unchecked")
  void resolve(KubernetesClient client, ControllerConfiguration<P> configuration) {
    if (!resolved) {
      dependentResourceNodes.values().forEach(drn -> drn.resolve(client, configuration));
      resolved = true;
    }
  }

  boolean hasCleaner() {
    return hasCleaner;
  }

  static boolean isDeletable(Class<? extends DependentResource> drClass) {
    final var isDeleter = Deleter.class.isAssignableFrom(drClass);
    if (!isDeleter) {
      return false;
    }

    if (KubernetesDependentResource.class.isAssignableFrom(drClass)) {
      return !GarbageCollected.class.isAssignableFrom(drClass);
    }
    return true;
  }
}
