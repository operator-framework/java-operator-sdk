package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReadyCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReconcileCondition;

public class WorkflowReconcileExecutor<P extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);

  private final Workflow<P> workflow;

  private final Set<DependentResourceNode<?, ?>> alreadyReconciled = ConcurrentHashMap.newKeySet();
  private final Set<DependentResourceNode<?, ?>> errored = ConcurrentHashMap.newKeySet();
  private final Set<DependentResourceNode<?, ?>> notReady =
      ConcurrentHashMap.newKeySet();
  private final Set<DependentResourceNode<?, ?>> reconcileConditionOrParentsConditionNotMet =
      ConcurrentHashMap.newKeySet();

  private final Map<DependentResourceNode<?, ?>, Future<?>> actualExecutions =
      new ConcurrentHashMap<>();
  private final List<Exception> exceptionsDuringExecution =
      Collections.synchronizedList(new ArrayList<>());

  private final P primary;
  private final Context<P> context;

  public WorkflowReconcileExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.primary = primary;
    this.context = context;
    this.workflow = workflow;
  }

  // add reconcile results
  public synchronized void reconcile() {
    for (DependentResourceNode<?, P> dependentResourceNode : workflow
        .getTopLevelDependentResources()) {
      handleReconcile(dependentResourceNode, false);
    }
    while (true) {
      try {
        this.wait();
        if (!exceptionsDuringExecution.isEmpty()) {
          log.debug("Exception during reconciliation for: {}", primary);
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

  private synchronized void handleReconcile(
      DependentResourceNode<?, P> dependentResourceNode,
      boolean onlyReconcileForPossibleDelete) {
    log.debug("Submitting for reconcile: {}", dependentResourceNode);

    if (alreadyReconciled(dependentResourceNode)
        || isReconcilingNow(dependentResourceNode)
        || !allParentsReconciledAndReady(dependentResourceNode)
        || hasErroredParent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

    if (onlyReconcileForPossibleDelete) {
      reconcileConditionOrParentsConditionNotMet.add(dependentResourceNode);
    } else {
      dependentResourceNode.getReconcileCondition()
          .ifPresent(reconcileCondition -> handleReconcileCondition(dependentResourceNode,
              reconcileCondition));
    }

    Future<?> nodeFuture =
        workflow.getExecutorService().submit(
            new NodeExecutor(dependentResourceNode,
                ownOrParentsReconcileConditionNotMet(dependentResourceNode)));
    actualExecutions.put(dependentResourceNode, nodeFuture);
    log.debug("Submitted to reconcile: {}", dependentResourceNode);
  }


  private synchronized void handleExceptionInExecutor(DependentResourceNode dependentResourceNode,
      RuntimeException e) {
    exceptionsDuringExecution.add(e);
    errored.add(dependentResourceNode);
  }

  private synchronized void handleNodeExecutionFinish(DependentResourceNode dependentResourceNode) {
    log.debug("Finished execution for: {}", dependentResourceNode);
    actualExecutions.remove(dependentResourceNode);
    if (actualExecutions.isEmpty()) {
      this.notifyAll();
    }
  }

  // needs to be synced
  private synchronized void updateStatusForNotReady(ReadyCondition<?, P> readyCondition) {
    readyCondition.addNotReadyStatusInfo(primary);
  }

  // needs to be in one step
  private synchronized void setAlreadyReconciledButNotReady(
      DependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Setting already reconciled but not ready for: {}", dependentResourceNode);
    alreadyReconciled.add(dependentResourceNode);
    notReady.add(dependentResourceNode);
  }

  private boolean ownOrParentsReconcileConditionNotMet(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return reconcileConditionOrParentsConditionNotMet.contains(dependentResourceNode) ||
        dependentResourceNode.getDependsOn().stream()
            .anyMatch(reconcileConditionOrParentsConditionNotMet::contains);
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
        DependentResource dependentResource = dependentResourceNode.getDependentResource();
        boolean ready = true;
        if (onlyReconcileForPossibleDelete) {
          if (dependentResource instanceof Deleter) {
            ((Deleter<P>) dependentResource).delete(primary, context);
          }
        } else {
          dependentResource.reconcile(primary, context);
          if (dependentResourceNode.getReadyCondition().isPresent()
              && !dependentResourceNode.getReadyCondition().get()
                  .isMet(dependentResource, primary, context)) {
            ready = false;
            updateStatusForNotReady(dependentResourceNode.getReadyCondition().get());
          }
        }

        if (ready) {
          log.debug("Setting already reconciled for: {}", dependentResourceNode);
          alreadyReconciled.add(dependentResourceNode);
          handleDependentsReconcile(dependentResourceNode, onlyReconcileForPossibleDelete);
        } else {
          setAlreadyReconciledButNotReady(dependentResourceNode);
        }
      } catch (RuntimeException e) {
        handleExceptionInExecutor(dependentResourceNode, e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }

  private boolean isReconcilingNow(DependentResourceNode<?, ?> dependentResourceNode) {
    return actualExecutions.containsKey(dependentResourceNode);
  }

  private synchronized void handleDependentsReconcile(
      DependentResourceNode<?, P> dependentResourceNode, boolean onlyReconcileForPossibleDelete) {
    var dependents = workflow.getDependents().get(dependentResourceNode);
    if (dependents != null) {

      dependents.forEach(d -> {
        log.debug("Handle reconcile for dependent: {} of parent:{}", d, dependentResourceNode);
        handleReconcile(d, onlyReconcileForPossibleDelete);
      });
    }
  }

  private boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty();
  }

  private AggregatedOperatorException createFinalException() {
    return new AggregatedOperatorException("Exception during workflow.", exceptionsDuringExecution);
  }

  private boolean alreadyReconciled(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return alreadyReconciled.contains(dependentResourceNode);
  }


  private void handleReconcileCondition(DependentResourceNode<?, ?> dependentResourceNode,
      ReconcileCondition reconcileCondition) {
    boolean conditionMet =
        reconcileCondition.isMet(dependentResourceNode.getDependentResource(), primary, context);
    if (!conditionMet) {
      reconcileConditionOrParentsConditionNotMet.add(dependentResourceNode);
    }
  }

  private boolean allParentsReconciledAndReady(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return dependentResourceNode.getDependsOn().isEmpty()
        || dependentResourceNode.getDependsOn().stream()
            .allMatch(d -> alreadyReconciled(d) && !notReady.contains(d));
  }

  private boolean hasErroredParent(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return !dependentResourceNode.getDependsOn().isEmpty()
        && dependentResourceNode.getDependsOn().stream()
            .anyMatch(errored::contains);
  }
}
