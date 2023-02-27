package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

abstract class NodeExecutor<R, P extends HasMetadata> implements Runnable {

  private final DependentResourceNode<R, P> dependentResourceNode;
  private final AbstractWorkflowExecutor<P> workflowExecutor;

  protected NodeExecutor(DependentResourceNode<R, P> dependentResourceNode,
      AbstractWorkflowExecutor<P> workflowExecutor) {
    this.dependentResourceNode = dependentResourceNode;
    this.workflowExecutor = workflowExecutor;
  }

  @Override
  public void run() {
    try {
      var dependentResource = dependentResourceNode.getDependentResource();

      doRun(dependentResourceNode, dependentResource);

    } catch (Throwable e) {
      workflowExecutor.handleExceptionInExecutor(dependentResourceNode, new RuntimeException(e));
    } finally {
      workflowExecutor.handleNodeExecutionFinish(dependentResourceNode);
    }
  }

  protected abstract void doRun(DependentResourceNode<R, P> dependentResourceNode,
      DependentResource<R, P> dependentResource);
}
