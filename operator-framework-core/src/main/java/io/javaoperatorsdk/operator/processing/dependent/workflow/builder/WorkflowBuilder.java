package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow.THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowBuilder<P extends HasMetadata> {

  private final Set<DependentResourceNode<?, P>> dependentResourceNodes = new HashSet<>();
  private boolean throwExceptionAutomatically = THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

  public DependentBuilder<P> addDependentResource(DependentResource dependentResource) {
    DependentResourceNode node = new DependentResourceNode<>(dependentResource);
    dependentResourceNodes.add(node);
    return new DependentBuilder<>(this, node);
  }

  void addDependentResourceNode(DependentResourceNode node) {
    dependentResourceNodes.add(node);
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
    return new Workflow(dependentResourceNodes,
        ConfigurationServiceProvider.instance().getExecutorService(), throwExceptionAutomatically);
  }

  public Workflow<P> build(int parallelism) {
    return new Workflow(dependentResourceNodes, parallelism);
  }

  public Workflow<P> build(ExecutorService executorService) {
    return new Workflow(dependentResourceNodes, executorService, throwExceptionAutomatically);
  }
}
