package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class WorkflowReconcileExecutor<P extends HasMetadata> {

  // todo add log messages
  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);

  private Workflow<P> workflow;

  private Set<DependentResourceNode> alreadyReconciled = ConcurrentHashMap.newKeySet();
  private Set<Future<?>> actualExecutions = ConcurrentHashMap.newKeySet();
  private Map<DependentResourceNode, Future<?>> nodeToFuture = new ConcurrentHashMap<>();
  private List<Exception> exceptionsDuringExecution =
      Collections.synchronizedList(new ArrayList<>());
  private Set<DependentResourceNode> markedToReconcileAgain = ConcurrentHashMap.newKeySet();

  private final P primary;
  private final Context<P> context;

  public WorkflowReconcileExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.primary = primary;
    this.context = context;
    this.workflow = workflow;
  }

  public synchronized void reconcile() {
    for (DependentResourceNode dependentResourceNode : workflow.getTopLevelDependentResources()) {
      submitForReconcile(dependentResourceNode);
    }
    while (true) {
      try {
        this.wait();
        if (exceptionsPresent()) {
          throw createFinalException();
        } else if (noMoreExecutionsScheduled()) {
          break;
        }
      } catch (InterruptedException e) {
        // todo check this better
        throw new IllegalStateException(e);
      }
    }
  }

  private AggregatedOperatorException createFinalException() {
    return new AggregatedOperatorException("Exception during workflow.", exceptionsDuringExecution);
  }

  private synchronized boolean alreadyReconciled(DependentResourceNode dependentResourceNode) {
    return alreadyReconciled.contains(dependentResourceNode);
  }

  private synchronized boolean allDependOnsReconciled(DependentResourceNode dependentResourceNode) {
    return dependentResourceNode.getDependsOnRelations().stream()
        .allMatch(relation -> alreadyReconciled(relation.getDependsOn()));
  }

  private synchronized void handleNodeExecutionFinish(DependentResourceNode dependentResourceNode) {

    var future = nodeToFuture.remove(dependentResourceNode);
    actualExecutions.remove(future);

    if (exceptionsPresent()) {
      if (actualExecutions.isEmpty()) {
        notifyMainReconcile();
      }
      return;
    }

    if (markedToReconcileAgain.contains(dependentResourceNode)
        && alreadyReconciled(dependentResourceNode)) {
      log.warn("Something happened, this never should be the case.");
    }
    if (markedToReconcileAgain.contains(dependentResourceNode)
        && !alreadyReconciled(dependentResourceNode)) {
      markedToReconcileAgain.remove(dependentResourceNode);
      submitForReconcile(dependentResourceNode);
    }
    if (actualExecutions.isEmpty()) {
      notifyMainReconcile();
    }
  }

  private synchronized void submitForReconcile(DependentResourceNode dependentResourceNode) {
    if (nodeToFuture.containsKey(dependentResourceNode)) {
      markedToReconcileAgain.add(dependentResourceNode);
      return;
    }

    Future<?> nodeFuture =
        workflow.getExecutorService().submit(new NodeExecutor(dependentResourceNode));
    actualExecutions.add(nodeFuture);
    nodeToFuture.put(dependentResourceNode, nodeFuture);
  }

  private synchronized void executeDependents(DependentResourceNode dependentResourceNode) {
    if (!exceptionsPresent()) {
      workflow.getReverseDependsOn().get(dependentResourceNode).forEach(this::submitForReconcile);
    }
  }

  private synchronized void handleExceptionInExecutor(
      DependentResourceNode dependentResourceNode, RuntimeException e) {
    exceptionsDuringExecution.add(e);
    markedToReconcileAgain.clear();
    // todo optimize to cancel futures?
    // actualExecutions.forEach(actualExecution -> actualExecution.cancel(false));
    // var doneExecutions =
    // actualExecutions.stream().filter(Future::isDone).collect(Collectors.toSet());
    // actualExecutions.removeAll(doneExecutions);
  }

  private boolean exceptionsPresent() {
    return !exceptionsDuringExecution.isEmpty();
  }

  private boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty() && markedToReconcileAgain.isEmpty();
  }

  private synchronized void notifyMainReconcile() {
    this.notifyAll();
  }

  private class NodeExecutor implements Runnable {

    private final DependentResourceNode dependentResourceNode;

    private NodeExecutor(DependentResourceNode dependentResourceNode) {
      this.dependentResourceNode = dependentResourceNode;
    }

    // todo conditions
    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        if (alreadyReconciled(dependentResourceNode)
            || allDependOnsReconciled(dependentResourceNode)
            || exceptionsPresent()) {
          return;
        }
        dependentResourceNode.getDependentResource().reconcile(primary, context);
        alreadyReconciled.add(dependentResourceNode);
        executeDependents(dependentResourceNode);
      } catch (RuntimeException e) {
        handleExceptionInExecutor(dependentResourceNode, e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }
}
