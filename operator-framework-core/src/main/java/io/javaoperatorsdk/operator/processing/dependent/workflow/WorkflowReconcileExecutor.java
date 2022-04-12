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
        }
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
  }

  private AggregatedOperatorException createFinalException() {
    return new AggregatedOperatorException("Exception during workflow.", exceptionsDuringExecution);
  }

  private synchronized boolean alreadyReconciled(DependentResourceNode dependentResourceNode) {
    return alreadyReconciled.contains(dependentResourceNode);
  }

  private synchronized boolean allDependOnsReconciled(DependentResourceNode dependentResourceNode) {
    return dependentResourceNode.getDependsOnRelations().isEmpty()
        || dependentResourceNode.getDependsOnRelations().stream()
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
      log.warn("Marked to reconcile but already reconciled, this should not happen. DR: {}",
          dependentResourceNode);
    }

    if (markedToReconcileAgain.contains(dependentResourceNode)
        && !alreadyReconciled(dependentResourceNode)) {
      markedToReconcileAgain.remove(dependentResourceNode);
      log.debug("Submitting marked resource to reconcile: {}", dependentResourceNode);
      submitForReconcile(dependentResourceNode);
    }
    if (actualExecutions.isEmpty()) {
      notifyMainReconcile();
    }
  }

  private synchronized void submitForReconcile(DependentResourceNode dependentResourceNode) {
    log.debug("Submitting for reconcile: {}", dependentResourceNode);

    if (alreadyReconciled(dependentResourceNode)
        || !allDependOnsReconciled(dependentResourceNode)
        || exceptionsPresent()) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

    if (nodeToFuture.containsKey(dependentResourceNode)) {
      log.debug("The same dependent resource already bein reconciled," +
          " marking it for future reconciliation: {}", dependentResourceNode);
      markedToReconcileAgain.add(dependentResourceNode);
      return;
    }


    Future<?> nodeFuture =
        workflow.getExecutorService().submit(new NodeExecutor(dependentResourceNode));
    actualExecutions.add(nodeFuture);
    nodeToFuture.put(dependentResourceNode, nodeFuture);
    log.debug("Submitted to reconcile: {}", dependentResourceNode);
  }

  private synchronized void submitDependents(DependentResourceNode dependentResourceNode) {
    if (!exceptionsPresent()) {
      var dependents = workflow.getDependents().get(dependentResourceNode);
      if (dependents != null) {
        dependents.forEach(this::submitForReconcile);
      }
    }
  }

  private synchronized void handleExceptionInExecutor(RuntimeException e) {
    exceptionsDuringExecution.add(e);
    markedToReconcileAgain.clear();
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

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        if (exceptionsPresent()) {
          return;
        }
        dependentResourceNode.getDependentResource().reconcile(primary, context);
        alreadyReconciled.add(dependentResourceNode);
        submitDependents(dependentResourceNode);
      } catch (RuntimeException e) {
        handleExceptionInExecutor(e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }
}
