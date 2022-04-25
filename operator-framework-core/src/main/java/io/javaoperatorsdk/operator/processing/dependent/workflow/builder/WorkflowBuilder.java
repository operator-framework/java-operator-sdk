package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;

public class WorkflowBuilder<P extends HasMetadata> {

  private Set<DependentResourceNode<?, P>> dependentResourceNodes = new HashSet<>();

  public DependentBuilder<P> addDependent(DependentResource<?, P> dependentResource) {
    DependentResourceNode<?, P> node = new DependentResourceNode<>(dependentResource);
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

  public Workflow<P> build() {
    return new Workflow<>(dependentResourceNodes,
        ConfigurationServiceProvider.instance().getExecutorService());
  }

  public Workflow<P> build(int parallelism) {
    return new Workflow<>(dependentResourceNodes, parallelism);
  }

  public Workflow<P> build(ExecutorService executorService) {
    return new Workflow<>(dependentResourceNodes, executorService);
  }
}
