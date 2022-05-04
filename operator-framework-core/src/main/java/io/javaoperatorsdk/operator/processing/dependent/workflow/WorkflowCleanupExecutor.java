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

public class WorkflowCleanupExecutor<P extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);

  private final Map<DependentResourceNode<?, ?>, Future<?>> actualExecutions =
      new HashMap<>();
  private final Map<DependentResourceNode<?, ?>, Exception> exceptionsDuringExecution =
      new HashMap<>();
  private final Set<DependentResourceNode<?, ?>> alreadyReconciled = new HashSet<>();
  private final Set<DependentResourceNode<?, ?>> notReady = new HashSet<>();
  private final Set<DependentResourceNode<?, ?>> ownOrAncestorReconcileConditionConditionNotMet =
      new HashSet<>();

  private final Workflow<P> workflow;
  private final P primary;
  private final Context<P> context;

  public WorkflowCleanupExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.workflow = workflow;
    this.primary = primary;
    this.context = context;
  }


  public synchronized WorkflowCleanupResult cleanup() {
    for (DependentResourceNode<?, P> dependentResourceNode : workflow
        .getBottomLevelResource()) {
      handleCleanup(dependentResourceNode, false);
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

  private WorkflowCleanupResult createCleanupResult() {
    return new WorkflowCleanupResult();
  }

  private synchronized boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty();
  }

  private void handleCleanup(DependentResourceNode<?, P> dependentResourceNode, boolean b) {
    log.debug("Submitting for cleanup: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isCleaningNow(dependentResourceNode)
        || !allParentsCleaned(dependentResourceNode)
        || hasErroredParent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

  }

  private class NodeExecutor implements Runnable {

    private final DependentResourceNode<?, P> dependentResourceNode;
    private final boolean onlyReconcileForPossibleDelete;

    private NodeExecutor(DependentResourceNode<?, P> dependentResourceNode,
        boolean onlyReconcileForDelete) {
      this.dependentResourceNode = dependentResourceNode;
      this.onlyReconcileForPossibleDelete = onlyReconcileForDelete;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {

      } catch (RuntimeException e) {
        handleExceptionInExecutor(dependentResourceNode, e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }

  private synchronized void handleExceptionInExecutor(DependentResourceNode dependentResourceNode,
      RuntimeException e) {
    exceptionsDuringExecution.put(dependentResourceNode, e);
  }

  private synchronized void handleNodeExecutionFinish(DependentResourceNode dependentResourceNode) {
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
    return alreadyReconciled.contains(dependentResourceNode);
  }

  private boolean allParentsCleaned(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return dependentResourceNode.getDependsOn().isEmpty()
        || dependentResourceNode.getDependsOn().stream()
            .allMatch(d -> alreadyVisited(d) && !notReady.contains(d));
  }

  private boolean hasErroredParent(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return !dependentResourceNode.getDependsOn().isEmpty()
        && dependentResourceNode.getDependsOn().stream()
            .anyMatch(exceptionsDuringExecution::containsKey);
  }
}
