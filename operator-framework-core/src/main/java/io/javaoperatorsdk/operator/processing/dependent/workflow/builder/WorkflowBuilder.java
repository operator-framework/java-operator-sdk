package io.javaoperatorsdk.operator.processing.dependent.workflow.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;

public class WorkflowBuilder<P extends HasMetadata> {

  private List<DependentResourceNode> dependentResourceNodes = new ArrayList<>();

  public DependentBuilder addDependent(DependentResource<?, P> dependentResource) {
    DependentResourceNode node = new DependentResourceNode(dependentResource);
    dependentResourceNodes.add(node);
    return new DependentBuilder(this, node);
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
    return new Workflow<>(dependentResourceNodes, Executors.newCachedThreadPool());
  }

  public Workflow<P> build(int parallelism) {
    return new Workflow<>(dependentResourceNodes, parallelism);
  }

  public Workflow<P> build(ExecutorService executorService) {
    return new Workflow<>(dependentResourceNodes, executorService);
  }
}
