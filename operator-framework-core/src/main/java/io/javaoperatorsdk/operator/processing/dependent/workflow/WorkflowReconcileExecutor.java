package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowReconcileExecutor<P extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);

  private final Workflow<P> workflow;

  /**
   * Covers both deleted and reconciled
   */
  private final Set<DefaultDependentResourceNode> alreadyVisited = ConcurrentHashMap.newKeySet();
  private final Set<DefaultDependentResourceNode> notReady = ConcurrentHashMap.newKeySet();
  private final Map<DefaultDependentResourceNode, Future<?>> actualExecutions =
      new HashMap<>();
  private final Map<DefaultDependentResourceNode, Exception> exceptionsDuringExecution =
      new ConcurrentHashMap<>();

  private final Set<DefaultDependentResourceNode> markedForDelete = ConcurrentHashMap.newKeySet();
  private final Set<DefaultDependentResourceNode> deletePostConditionNotMet =
      ConcurrentHashMap.newKeySet();
  // used to remember reconciled (not deleted or errored) dependents
  private final Set<DefaultDependentResourceNode> reconciled = ConcurrentHashMap.newKeySet();
  private final Map<DependentResource, ReconcileResult> reconcileResults =
      new ConcurrentHashMap<>();

  private final P primary;
  private final Context<P> context;

  public WorkflowReconcileExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.primary = primary;
    this.context = context;
    this.workflow = workflow;
  }

  public synchronized WorkflowReconcileResult reconcile() {
    for (DefaultDependentResourceNode dependentResourceNode : workflow
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

  private synchronized <R> void handleReconcile(
      DefaultDependentResourceNode<R, P> dependentResourceNode) {
    log.debug("Submitting for reconcile: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isReconcilingNow(dependentResourceNode)
        || !allParentsReconciledAndReady(dependentResourceNode)
        || markedForDelete.contains(dependentResourceNode)
        || hasErroredParent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

    boolean reconcileConditionMet = dependentResourceNode.getReconcilePrecondition()
        .map(rc -> rc.isMet(primary, dependentResourceNode.getSecondaryResource(primary, context),
            context))
        .orElse(true);

    if (!reconcileConditionMet) {
      handleReconcileConditionNotMet(dependentResourceNode);
    } else {
      Future<?> nodeFuture = workflow.getExecutorService()
          .submit(new NodeReconcileExecutor(dependentResourceNode));
      actualExecutions.put(dependentResourceNode, nodeFuture);
      log.debug("Submitted to reconcile: {}", dependentResourceNode);
    }
  }

  private synchronized void handleDelete(DefaultDependentResourceNode dependentResourceNode) {
    log.debug("Submitting for delete: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isReconcilingNow(dependentResourceNode)
        || !markedForDelete.contains(dependentResourceNode)
        || !allDependentsDeletedAlready(dependentResourceNode)) {
      log.debug("Skipping submit for delete of: {}, ", dependentResourceNode);
      return;
    }

    Future<?> nodeFuture = workflow.getExecutorService()
        .submit(new NodeDeleteExecutor(dependentResourceNode));
    actualExecutions.put(dependentResourceNode, nodeFuture);
    log.debug("Submitted to delete: {}", dependentResourceNode);
  }

  private boolean allDependentsDeletedAlready(
      DefaultDependentResourceNode<?, P> dependentResourceNode) {
    var dependents = dependentResourceNode.getParents();
    return dependents.stream().allMatch(d -> alreadyVisited.contains(d) && !notReady.contains(d)
        && !exceptionsDuringExecution.containsKey(d) && !deletePostConditionNotMet.contains(d));
  }


  private synchronized void handleExceptionInExecutor(
      DefaultDependentResourceNode dependentResourceNode,
      RuntimeException e) {
    exceptionsDuringExecution.put(dependentResourceNode, e);
  }

  private synchronized void handleNodeExecutionFinish(
      DefaultDependentResourceNode dependentResourceNode) {
    log.debug("Finished execution for: {}", dependentResourceNode);
    actualExecutions.remove(dependentResourceNode);
    if (actualExecutions.isEmpty()) {
      this.notifyAll();
    }
  }

  // needs to be in one step
  private synchronized void setAlreadyReconciledButNotReady(
      DefaultDependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Setting already reconciled but not ready for: {}", dependentResourceNode);
    alreadyVisited.add(dependentResourceNode);
    notReady.add(dependentResourceNode);
  }

  private class NodeReconcileExecutor<R> implements Runnable {

    private final DefaultDependentResourceNode<R, P> dependentResourceNode;

    private NodeReconcileExecutor(DefaultDependentResourceNode dependentResourceNode) {
      this.dependentResourceNode = dependentResourceNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        DependentResource dependentResource = dependentResourceNode.getDependentResource();
        if (log.isDebugEnabled()) {
          log.debug(
              "Reconciling {} for primary: {}",
              dependentResourceNode,
              ResourceID.fromResource(primary));
        }
        ReconcileResult reconcileResult = dependentResource.reconcile(primary, context);
        reconcileResults.put(dependentResource, reconcileResult);
        reconciled.add(dependentResourceNode);

        boolean ready = dependentResourceNode.getReadyPostcondition()
            .map(rc -> rc.isMet(primary,
                dependentResourceNode.getDependentResource().getSecondaryResource(primary, context)
                    .orElse(null),
                context))
            .orElse(true);

        if (ready) {
          log.debug("Setting already reconciled for: {}", dependentResourceNode);
          alreadyVisited.add(dependentResourceNode);
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

  private class NodeDeleteExecutor<R> implements Runnable {

    private final DefaultDependentResourceNode<R, P> dependentResourceNode;

    private NodeDeleteExecutor(DefaultDependentResourceNode<R, P> dependentResourceNode) {
      this.dependentResourceNode = dependentResourceNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        DependentResource dependentResource = dependentResourceNode.getDependentResource();
        var deletePostCondition = dependentResourceNode.getDeletePostcondition();

        if (dependentResource instanceof Deleter
            && !(dependentResource instanceof GarbageCollected)) {
          ((Deleter<P>) dependentResourceNode.getDependentResource()).delete(primary, context);
        }
        boolean deletePostConditionMet =
            deletePostCondition.map(c -> c.isMet(primary,
                dependentResourceNode.getDependentResource().getSecondaryResource(primary, context)
                    .orElse(null),
                context)).orElse(true);
        if (deletePostConditionMet) {
          alreadyVisited.add(dependentResourceNode);
          handleDependentDeleted(dependentResourceNode);
        } else {
          // updating alreadyVisited needs to be the last operation otherwise could lead to a race
          // condition in handleDelete condition checks
          deletePostConditionNotMet.add(dependentResourceNode);
          alreadyVisited.add(dependentResourceNode);
        }
      } catch (RuntimeException e) {
        handleExceptionInExecutor(dependentResourceNode, e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }

  private synchronized void handleDependentDeleted(
      DefaultDependentResourceNode<?, P> dependentResourceNode) {
    dependentResourceNode.getDependsOn().forEach(dr -> {
      log.debug("Handle deleted for: {} with dependent: {}", dr, dependentResourceNode);
      handleDelete(dr);
    });
  }

  private boolean isReconcilingNow(DefaultDependentResourceNode<?, P> dependentResourceNode) {
    return actualExecutions.containsKey(dependentResourceNode);
  }

  private synchronized void handleDependentsReconcile(
      DefaultDependentResourceNode<?, P> dependentResourceNode) {
    var dependents = dependentResourceNode.getParents();
    dependents.forEach(d -> {
      log.debug("Handle reconcile for dependent: {} of parent:{}", d, dependentResourceNode);
      handleReconcile(d);
    });
  }

  private boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty();
  }

  private boolean alreadyVisited(
      DefaultDependentResourceNode<?, P> dependentResourceNode) {
    return alreadyVisited.contains(dependentResourceNode);
  }


  private void handleReconcileConditionNotMet(
      DefaultDependentResourceNode<?, P> dependentResourceNode) {
    Set<DefaultDependentResourceNode> bottomNodes = new HashSet<>();
    markDependentsForDelete(dependentResourceNode, bottomNodes);
    bottomNodes.forEach(this::handleDelete);
  }

  private void markDependentsForDelete(DefaultDependentResourceNode<?, P> dependentResourceNode,
      Set<DefaultDependentResourceNode> bottomNodes) {
    markedForDelete.add(dependentResourceNode);
    var dependents = dependentResourceNode.getParents();
    if (dependents.isEmpty()) {
      bottomNodes.add(dependentResourceNode);
    } else {
      dependents.forEach(d -> markDependentsForDelete(d, bottomNodes));
    }
  }

  private boolean allParentsReconciledAndReady(
      DefaultDependentResourceNode<?, ?> dependentResourceNode) {
    return dependentResourceNode.getDependsOn().isEmpty()
        || dependentResourceNode.getDependsOn().stream()
        .allMatch(d -> alreadyVisited(d) && !notReady.contains(d));
  }

  private boolean hasErroredParent(
      DefaultDependentResourceNode<?, ?> dependentResourceNode) {
    return !dependentResourceNode.getDependsOn().isEmpty()
        && dependentResourceNode.getDependsOn().stream()
        .anyMatch(exceptionsDuringExecution::containsKey);
  }

  private WorkflowReconcileResult createReconcileResult() {
    return new WorkflowReconcileResult(
        reconciled.stream()
            .map(DefaultDependentResourceNode::getDependentResource).collect(Collectors.toList()),
        notReady.stream()
            .map(DefaultDependentResourceNode::getDependentResource)
            .collect(Collectors.toList()),
        exceptionsDuringExecution
            .entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getDependentResource(), Map.Entry::getValue)),
        reconcileResults);
  }

}
