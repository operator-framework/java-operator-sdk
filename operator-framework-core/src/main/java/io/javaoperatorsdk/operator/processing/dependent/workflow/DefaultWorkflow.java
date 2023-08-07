package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
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
public class DefaultWorkflow<P extends HasMetadata> implements Workflow<P> {

  private final Map<String, DependentResourceNode> dependentResourceNodes;
  private final Set<DependentResourceNode> topLevelResources;
  private final Set<DependentResourceNode> bottomLevelResource;

  private final boolean throwExceptionAutomatically;
  private final boolean hasCleaner;

  DefaultWorkflow(Set<DependentResourceNode> dependentResourceNodes) {
    this(dependentResourceNodes, THROW_EXCEPTION_AUTOMATICALLY_DEFAULT, false);
  }

  DefaultWorkflow(Set<DependentResourceNode> dependentResourceNodes,
      boolean throwExceptionAutomatically,
      boolean hasCleaner) {
    this.throwExceptionAutomatically = throwExceptionAutomatically;
    this.hasCleaner = hasCleaner;

    if (dependentResourceNodes == null) {
      this.topLevelResources = Collections.emptySet();
      this.bottomLevelResource = Collections.emptySet();
      this.dependentResourceNodes = Collections.emptyMap();
    } else {
      this.topLevelResources = new HashSet<>(dependentResourceNodes.size());
      this.bottomLevelResource = new HashSet<>(dependentResourceNodes);
      this.dependentResourceNodes = toMap(dependentResourceNodes);
    }
  }

  protected DefaultWorkflow(Map<String, DependentResourceNode> dependentResourceNodes,
      Set<DependentResourceNode> bottomLevelResource, Set<DependentResourceNode> topLevelResources,
      boolean throwExceptionAutomatically,
      boolean hasCleaner) {
    this.throwExceptionAutomatically = throwExceptionAutomatically;
    this.hasCleaner = hasCleaner;
    this.topLevelResources = topLevelResources;
    this.bottomLevelResource = bottomLevelResource;
    this.dependentResourceNodes = dependentResourceNodes;
  }

  @SuppressWarnings("unchecked")
  private Map<String, DependentResourceNode> toMap(Set<DependentResourceNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return Collections.emptyMap();
    }

    final var map = new HashMap<String, DependentResourceNode>(nodes.size());
    for (DependentResourceNode node : nodes) {
      // add cycle detection?
      if (node.getDependsOn().isEmpty()) {
        topLevelResources.add(node);
      } else {
        for (DependentResourceNode dependsOn : (List<DependentResourceNode>) node.getDependsOn()) {
          bottomLevelResource.remove(dependsOn);
        }
      }
      map.put(node.getName(), node);
    }
    if (topLevelResources.size() == 0) {
      throw new IllegalStateException(
          "No top-level dependent resources found. This might indicate a cyclic Set of DependentResourceNode has been provided.");
    }
    return map;
  }

  @Override
  public WorkflowReconcileResult reconcile(P primary, Context<P> context) {
    WorkflowReconcileExecutor<P> workflowReconcileExecutor =
        new WorkflowReconcileExecutor<>(this, primary, context);
    var result = workflowReconcileExecutor.reconcile();
    if (throwExceptionAutomatically) {
      result.throwAggregateExceptionIfErrorsPresent();
    }
    return result;
  }

  @Override
  public WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    WorkflowCleanupExecutor<P> workflowCleanupExecutor =
        new WorkflowCleanupExecutor<>(this, primary, context);
    var result = workflowCleanupExecutor.cleanup();
    if (throwExceptionAutomatically) {
      result.throwAggregateExceptionIfErrorsPresent();
    }
    return result;
  }

  @Override
  public Set<DependentResourceNode> getTopLevelDependentResources() {
    return topLevelResources;
  }

  @Override
  public Set<DependentResourceNode> getBottomLevelResource() {
    return bottomLevelResource;
  }

  @Override
  public boolean hasCleaner() {
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

  @Override
  public boolean isEmpty() {
    return dependentResourceNodes.isEmpty();
  }

  @Override
  public Map<String, DependentResource> getDependentResourcesByName() {
    final var resources = new HashMap<String, DependentResource>(dependentResourceNodes.size());
    dependentResourceNodes
        .forEach((name, node) -> resources.put(name, node.getDependentResource()));
    return resources;
  }
}
