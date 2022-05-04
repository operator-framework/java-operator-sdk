package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;

public class WorkflowCleanupExecutor<P extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);

  private final Map<DependentResourceNode<?, ?>, Future<?>> actualExecutions =
      new HashMap<>();
  private final Map<DependentResourceNode<?, ?>, Exception> exceptionsDuringExecution =
      new HashMap<>();
  private final Set<DependentResourceNode<?, ?>> alreadyVisited = new HashSet<>();
  private final Set<DependentResourceNode<?, ?>> notReady = new HashSet<>();

  private final Workflow<P> workflow;
  private final P primary;
  private final Context<P> context;

  public WorkflowCleanupExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.workflow = workflow;
    this.primary = primary;
    this.context = context;
  }

  // todo cleanup condition
  // todo error handling

  public synchronized WorkflowCleanupResult cleanup() {
    for (DependentResourceNode<?, P> dependentResourceNode : workflow
        .getBottomLevelResource()) {
      handleCleanup(dependentResourceNode);
    }
    while (true) {
      try {
        this.wait();
        if (noMoreExecutionsScheduled()) {
          break;
        } else {
          log.warn("Notified but still resources under execution. This should not happen.");
        }
      } catch (InterruptedException e) {
        log.warn("Thread interrupted", e);
        Thread.currentThread().interrupt();
      }
    }
    return createCleanupResult();
  }

  private synchronized boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty();
  }

  private synchronized void handleCleanup(DependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Submitting for cleanup: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isCleaningNow(dependentResourceNode)
        || !allParentsCleaned(dependentResourceNode)
        || hasErroredParent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

    Future<?> nodeFuture =
        workflow.getExecutorService().submit(
            new NodeExecutor(dependentResourceNode));
    actualExecutions.put(dependentResourceNode, nodeFuture);
    log.debug("Submitted to reconcile: {}", dependentResourceNode);
  }

  private class NodeExecutor implements Runnable {

    private final DependentResourceNode<?, P> dependentResourceNode;

    private NodeExecutor(DependentResourceNode<?, P> dependentResourceNode) {
      this.dependentResourceNode = dependentResourceNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        if (dependentResourceNode.getDependentResource() instanceof Deleter) {
          // todo check if not garbage collected
          ((Deleter<P>) dependentResourceNode.getDependentResource()).delete(primary, context);
        }
        handleDependentCleaned(dependentResourceNode);
      } catch (RuntimeException e) {
        handleExceptionInExecutor(dependentResourceNode, e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private synchronized void handleDependentCleaned(
      DependentResourceNode<?, P> dependentResourceNode) {
    var dependOns = dependentResourceNode.getDependsOn();
    if (dependOns != null) {
      dependOns.forEach(d -> {
        log.debug("Handle cleanup for dependent: {} of parent:{}", d, dependentResourceNode);
        handleCleanup(d);
      });
    }
  }

  private synchronized void handleExceptionInExecutor(
      DependentResourceNode<?, P> dependentResourceNode,
      RuntimeException e) {
    exceptionsDuringExecution.put(dependentResourceNode, e);
  }

  private synchronized void handleNodeExecutionFinish(
      DependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Finished execution for: {}", dependentResourceNode);
    actualExecutions.remove(dependentResourceNode);
    if (actualExecutions.isEmpty()) {
      this.notifyAll();
    }
  }

  private boolean isCleaningNow(DependentResourceNode<?, ?> dependentResourceNode) {
    return actualExecutions.containsKey(dependentResourceNode);
  }

  private boolean alreadyVisited(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return alreadyVisited.contains(dependentResourceNode);
  }

  private boolean allParentsCleaned(
      DependentResourceNode<?, ?> dependentResourceNode) {
    var parents = workflow.getDependents().get(dependentResourceNode);
    return parents.isEmpty()
        || parents.stream()
            .allMatch(d -> alreadyVisited(d) && !notReady.contains(d));
  }

  private boolean hasErroredParent(
      DependentResourceNode<?, ?> dependentResourceNode) {
    var parents = workflow.getDependents().get(dependentResourceNode);
    return !parents.isEmpty()
        && parents.stream().anyMatch(exceptionsDuringExecution::containsKey);
  }

  private WorkflowCleanupResult createCleanupResult() {
    return new WorkflowCleanupResult();
  }
}
