package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;

abstract class NodeExecutor<R, P extends HasMetadata> implements Runnable {

  private final DependentResourceNode<R, P> dependentResourceNode;
  private final AbstractWorkflowExecutor<P> workflowExecutor;

  protected NodeExecutor(
      DependentResourceNode<R, P> dependentResourceNode,
      AbstractWorkflowExecutor<P> workflowExecutor) {
    this.dependentResourceNode = dependentResourceNode;
    this.workflowExecutor = workflowExecutor;
  }

  @Override
  public void run() {
    try {
      doRun(dependentResourceNode);

    } catch (Exception e) {
      // Exception is required because of Kotlin
      workflowExecutor.handleExceptionInExecutor(dependentResourceNode, e);
    } finally {
      workflowExecutor.handleNodeExecutionFinish(dependentResourceNode);
    }
  }

  protected abstract void doRun(DependentResourceNode<R, P> dependentResourceNode);
}
