package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow.THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowBuilder<P extends HasMetadata> {

  private final Map<String, DefaultDependentResourceNode<?, P>> dependentResourceNodes =
      new HashMap<>();
  private boolean throwExceptionAutomatically = THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;
  private DefaultDependentResourceNode currentNode;
  private boolean isCleaner = false;

  public WorkflowBuilder<P> addDependentResource(DependentResource dependentResource) {
    currentNode = new DefaultDependentResourceNode<>(dependentResource);
    isCleaner = dependentResource.isDeletable();
    final var name = currentNode.getName();
    dependentResourceNodes.put(name, currentNode);
    return this;
  }

  public WorkflowBuilder<P> dependsOn(Set<DependentResource> dependentResources) {
    for (var dependentResource : dependentResources) {
      var dependsOn = getNodeByDependentResource(dependentResource);
      currentNode.addDependsOnRelation(dependsOn);
    }
    return this;
  }

  public WorkflowBuilder<P> dependsOn(DependentResource... dependentResources) {
    if (dependentResources != null) {
      return dependsOn(new HashSet<>(Arrays.asList(dependentResources)));
    }
    return this;
  }

  public WorkflowBuilder<P> withReconcilePrecondition(Condition reconcilePrecondition) {
    currentNode.setReconcilePrecondition(reconcilePrecondition);
    return this;
  }

  public WorkflowBuilder<P> withReadyPostcondition(Condition readyPostcondition) {
    currentNode.setReadyPostcondition(readyPostcondition);
    return this;
  }

  public WorkflowBuilder<P> withDeletePostcondition(Condition deletePostcondition) {
    currentNode.setDeletePostcondition(deletePostcondition);
    return this;
  }

  DependentResourceNode getNodeByDependentResource(DependentResource<?, ?> dependentResource) {
    // first check by name
    final var node =
        dependentResourceNodes.get(DefaultDependentResourceNode.getNameFor(dependentResource));
    if (node != null) {
      return node;
    } else {
      return dependentResourceNodes.values().stream()
          .filter(dr -> dr.getDependentResource() == dependentResource)
          .findFirst()
          .orElseThrow();
    }
  }

  public boolean isThrowExceptionAutomatically() {
    return throwExceptionAutomatically;
  }

  public WorkflowBuilder<P> withThrowExceptionFurther(boolean throwExceptionFurther) {
    this.throwExceptionAutomatically = throwExceptionFurther;
    return this;
  }

  public Workflow<P> build() {
    return build(ExecutorServiceManager.instance().workflowExecutorService());
  }

  public Workflow<P> build(int parallelism) {
    return build(Executors.newFixedThreadPool(parallelism));
  }

  public Workflow<P> build(ExecutorService executorService) {
    // workflow has been built from dependent resources so it is already resolved
    return new Workflow(new HashSet<>(dependentResourceNodes.values()), executorService,
        throwExceptionAutomatically, true, isCleaner);
  }
}
