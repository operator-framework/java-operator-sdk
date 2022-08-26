package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow.THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowBuilder<P extends HasMetadata> {

  private final Set<DependentResourceNode<?, P>> dependentResourceNodes = new HashSet<>();
  private boolean throwExceptionAutomatically = THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

  private DependentResourceNode currentNode;

  public WorkflowBuilder<P> addDependentResource(DependentResource dependentResource) {
    currentNode = new DependentResourceNode<>(dependentResource);
    dependentResourceNodes.add(currentNode);
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
    return dependentResourceNodes.stream()
        .filter(dr -> dr.getDependentResource() == dependentResource)
        .findFirst()
        .orElseThrow();
  }

  public boolean isThrowExceptionAutomatically() {
    return throwExceptionAutomatically;
  }

  public WorkflowBuilder<P> withThrowExceptionFurther(boolean throwExceptionFurther) {
    this.throwExceptionAutomatically = throwExceptionFurther;
    return this;
  }

  public Workflow<P> build() {
    return new Workflow(
        dependentResourceNodes, ExecutorServiceManager.instance().workflowExecutorService(),
        throwExceptionAutomatically);
  }

  public Workflow<P> build(int parallelism) {
    return new Workflow(dependentResourceNodes, parallelism);
  }

  public Workflow<P> build(ExecutorService executorService) {
    return new Workflow(dependentResourceNodes, executorService, throwExceptionAutomatically);
  }
}
