package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

public class WorkflowReconcileExecutor<P extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);

  private final Workflow<P> workflow;

  /** Covers both deleted and reconciled */
  private final Set<DependentResourceNode<?, ?>> alreadyReconciled = new HashSet<>();
  private final Set<DependentResourceNode<?, ?>> notReady = new HashSet<>();
  private final Map<DependentResourceNode<?, ?>, Future<?>> actualExecutions =
      new HashMap<>();
  private final Map<DependentResourceNode<?, ?>, Exception> exceptionsDuringExecution =
      new HashMap<>();

  private final Set<DependentResourceNode<?, ?>> markedForDelete = new HashSet<>();
  private final Set<DependentResourceNode<?, ?>> deleteConditionNotMet = new HashSet<>();

  private final P primary;
  private final Context<P> context;

  public WorkflowReconcileExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.primary = primary;
    this.context = context;
    this.workflow = workflow;
  }

  // todo reverse delete order

  // todo add reconcile results
  // - reconciled in results should contain only truly reconciled
  public synchronized WorkflowExecutionResult reconcile() {
    for (DependentResourceNode<?, P> dependentResourceNode : workflow
        .getTopLevelDependentResources()) {
      handleReconcile(dependentResourceNode);
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
    return createReconcileResult();
  }

  private synchronized void handleReconcile(
      DependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Submitting for reconcile: {}", dependentResourceNode);

    if (alreadyReconciled(dependentResourceNode)
        || isReconcilingNow(dependentResourceNode)
        || !allParentsReconciledAndReady(dependentResourceNode)
        || markedForDelete.contains(dependentResourceNode)
        || hasErroredParent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

    boolean reconcileConditionMet = dependentResourceNode.getReconcileCondition().map(
        rc -> rc.isMet(dependentResourceNode.getDependentResource(), primary, context))
        .orElse(true);

    if (!reconcileConditionMet) {
      handleReconcileConditionNotMet(dependentResourceNode);
    } else {
      Future<?> nodeFuture =
          workflow
              .getExecutorService()
              .submit(
                  new NodeReconcileExecutor(
                      dependentResourceNode));
      actualExecutions.put(dependentResourceNode, nodeFuture);
      log.debug("Submitted to reconcile: {}", dependentResourceNode);
    }
  }

  private void handleDelete(DependentResourceNode dependentResourceNode) {
    log.debug("Submitting for delete: {}", dependentResourceNode);

    if (alreadyReconciled(dependentResourceNode)
        || isReconcilingNow(dependentResourceNode)
        || !markedForDelete.contains(dependentResourceNode)
        || !allDependentsDeletedAlready(dependentResourceNode)) {
      log.debug("Skipping submit for delete of: {}, ", dependentResourceNode);
      return;
    }

    Future<?> nodeFuture =
        workflow.getExecutorService()
            .submit(new NodeDeleteExecutor(dependentResourceNode));
    actualExecutions.put(dependentResourceNode, nodeFuture);
    log.debug("Submitted to delete: {}", dependentResourceNode);
  }

  private boolean allDependentsDeletedAlready(DependentResourceNode dependentResourceNode) {
    var dependents = workflow.getDependents(dependentResourceNode);
    return dependents.stream().allMatch(d -> alreadyReconciled.contains(d) && !notReady.contains(d)
        && !exceptionsDuringExecution.containsKey(d));
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

  // needs to be in one step
  private synchronized void setAlreadyReconciledButNotReady(
      DependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Setting already reconciled but not ready for: {}", dependentResourceNode);
    alreadyReconciled.add(dependentResourceNode);
    notReady.add(dependentResourceNode);
  }

  private class NodeReconcileExecutor implements Runnable {

    private final DependentResourceNode<?, P> dependentResourceNode;

    private NodeReconcileExecutor(DependentResourceNode<?, P> dependentResourceNode) {
      this.dependentResourceNode = dependentResourceNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        DependentResource dependentResource = dependentResourceNode.getDependentResource();
        boolean ready = true;

        dependentResource.reconcile(primary, context);
        if (dependentResourceNode.getReadyCondition().isPresent()
            && !dependentResourceNode.getReadyCondition().get()
                .isMet(dependentResource, primary, context)) {
          ready = false;
        }
        if (ready) {
          log.debug("Setting already reconciled for: {}", dependentResourceNode);
          alreadyReconciled.add(dependentResourceNode);
          handleDependentsReconcile(dependentResourceNode);
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

  private class NodeDeleteExecutor implements Runnable {

    private final DependentResourceNode<?, P> dependentResourceNode;

    private NodeDeleteExecutor(DependentResourceNode<?, P> dependentResourceNode) {
      this.dependentResourceNode = dependentResourceNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        DependentResource dependentResource = dependentResourceNode.getDependentResource();
        var deletePostCondition = dependentResourceNode.getDeletePostCondition();

        if (dependentResource instanceof Deleter
            && !(dependentResource instanceof GarbageCollected)) {
          ((Deleter<P>) dependentResourceNode.getDependentResource()).delete(primary, context);
        }
        alreadyReconciled.add(dependentResourceNode);
        boolean deletePostConditionMet =
            deletePostCondition.map(c -> c.isMet(dependentResource, primary, context)).orElse(true);
        if (deletePostConditionMet) {
          handleDependentDeleted(dependentResourceNode);
        } else {
          deleteConditionNotMet.add(dependentResourceNode);
        }
      } catch (RuntimeException e) {
        handleExceptionInExecutor(dependentResourceNode, e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }

  private synchronized void handleDependentDeleted(
      DependentResourceNode<?, P> dependentResourceNode) {
    dependentResourceNode.getDependsOn().forEach(dr -> {
      log.debug("Handle deleted for: {} with dependent: {}", dr, dependentResourceNode);
      handleDelete(dr);
    });
  }

  private boolean isReconcilingNow(DependentResourceNode<?, ?> dependentResourceNode) {
    return actualExecutions.containsKey(dependentResourceNode);
  }

  private synchronized void handleDependentsReconcile(
      DependentResourceNode<?, P> dependentResourceNode) {
    var dependents = workflow.getDependents(dependentResourceNode);
    dependents.forEach(d -> {
      log.debug("Handle reconcile for dependent: {} of parent:{}", d, dependentResourceNode);
      handleReconcile(d);
    });
  }

  private boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty();
  }

  private boolean alreadyReconciled(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return alreadyReconciled.contains(dependentResourceNode);
  }


  private void handleReconcileConditionNotMet(DependentResourceNode<?, ?> dependentResourceNode) {
    Set<DependentResourceNode> bottomNodes = new HashSet<>();
    markDependentsForDelete(dependentResourceNode, bottomNodes);
    bottomNodes.forEach(bn -> handleDelete(bn));
  }

  private void markDependentsForDelete(DependentResourceNode<?, ?> dependentResourceNode,
      Set<DependentResourceNode> bottomNodes) {
    markedForDelete.add(dependentResourceNode);
    var dependents = workflow.getDependents(dependentResourceNode);
    if (dependents.isEmpty()) {
      bottomNodes.add(dependentResourceNode);
    } else {
      dependents.forEach(d -> markDependentsForDelete(d, bottomNodes));
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
            .anyMatch(exceptionsDuringExecution::containsKey);
  }

  private WorkflowExecutionResult createReconcileResult() {
    WorkflowExecutionResult workflowExecutionResult = new WorkflowExecutionResult();

    workflowExecutionResult.setErroredDependents(exceptionsDuringExecution
        .entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().getDependentResource(), Map.Entry::getValue)));
    workflowExecutionResult.setNotReadyDependents(notReady.stream()
        .map(DependentResourceNode::getDependentResource)
        .collect(Collectors.toList()));

    workflowExecutionResult.setReconciledDependents(alreadyReconciled.stream()
        .map(DependentResourceNode::getDependentResource).collect(Collectors.toList()));

    var notReconciledDependentResources =
        new HashSet<DependentResourceNode<?, ?>>(workflow.getDependents().keySet());
    notReconciledDependentResources.removeAll(alreadyReconciled);
    workflowExecutionResult.setNotReconciledDependents(notReconciledDependentResources.stream()
        .map(DependentResourceNode::getDependentResource).collect(Collectors.toList()));

    return workflowExecutionResult;
  }

}
