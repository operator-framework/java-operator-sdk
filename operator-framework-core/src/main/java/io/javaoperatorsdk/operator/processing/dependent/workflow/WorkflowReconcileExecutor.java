package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowReconcileExecutor<P extends HasMetadata> extends AbstractWorkflowExecutor<P> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);


  private final Set<DependentResourceNode> notReady = ConcurrentHashMap.newKeySet();

  private final Set<DependentResourceNode> markedForDelete = ConcurrentHashMap.newKeySet();
  private final Set<DependentResourceNode> deletePostConditionNotMet =
      ConcurrentHashMap.newKeySet();
  // used to remember reconciled (not deleted or errored) dependents
  private final Set<DependentResourceNode> reconciled = ConcurrentHashMap.newKeySet();
  private final Map<DependentResource, ReconcileResult> reconcileResults =
      new ConcurrentHashMap<>();

  public WorkflowReconcileExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    super(workflow, primary, context);
  }

  public synchronized WorkflowReconcileResult reconcile() {
    for (DependentResourceNode dependentResourceNode : workflow.getTopLevelDependentResources()) {
      handleReconcile(dependentResourceNode);
    }
    waitForScheduledExecutionsToRun();
    return createReconcileResult();
  }

  @Override
  protected Logger logger() {
    return log;
  }

  private synchronized <R> void handleReconcile(DependentResourceNode<R, P> dependentResourceNode) {
    log.debug("Submitting for reconcile: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isExecutingNow(dependentResourceNode)
        || !allParentsReconciledAndReady(dependentResourceNode)
        || markedForDelete.contains(dependentResourceNode)
        || hasErroredParent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

    boolean reconcileConditionMet = isConditionMet(dependentResourceNode.getReconcilePrecondition(),
        dependentResourceNode.getDependentResource());
    if (!reconcileConditionMet) {
      handleReconcileConditionNotMet(dependentResourceNode);
    } else {
      Future<?> nodeFuture = ExecutorServiceManager.instance().workflowExecutorService()
          .submit(new NodeReconcileExecutor(dependentResourceNode));
      markAsExecuting(dependentResourceNode, nodeFuture);
      log.debug("Submitted to reconcile: {}", dependentResourceNode);
    }
  }

  private synchronized void handleDelete(DependentResourceNode dependentResourceNode) {
    log.debug("Submitting for delete: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isExecutingNow(dependentResourceNode)
        || !markedForDelete.contains(dependentResourceNode)
        || !allDependentsDeletedAlready(dependentResourceNode)) {
      log.debug("Skipping submit for delete of: {}, ", dependentResourceNode);
      return;
    }

    Future<?> nodeFuture = ExecutorServiceManager.instance().workflowExecutorService()
        .submit(new NodeDeleteExecutor(dependentResourceNode));
    markAsExecuting(dependentResourceNode, nodeFuture);
    log.debug("Submitted to delete: {}", dependentResourceNode);
  }

  private boolean allDependentsDeletedAlready(DependentResourceNode<?, P> dependentResourceNode) {
    var dependents = dependentResourceNode.getParents();
    return dependents.stream().allMatch(d -> alreadyVisited(d) && !notReady.contains(d)
        && !isInError(d) && !deletePostConditionNotMet.contains(d));
  }

  // needs to be in one step
  private synchronized void setAlreadyReconciledButNotReady(
      DependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Setting already reconciled but not ready for: {}", dependentResourceNode);
    markAsVisited(dependentResourceNode);
    notReady.add(dependentResourceNode);
  }

  private class NodeReconcileExecutor<R> extends NodeExecutor<R, P> {

    private NodeReconcileExecutor(DependentResourceNode<R, P> dependentResourceNode) {
      super(dependentResourceNode, WorkflowReconcileExecutor.this);
    }

    @Override
    protected void doRun(DependentResourceNode<R, P> dependentResourceNode,
        DependentResource<R, P> dependentResource) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Reconciling {} for primary: {}",
            dependentResourceNode,
            ResourceID.fromResource(primary));
      }
      ReconcileResult reconcileResult = dependentResource.reconcile(primary, context);
      reconcileResults.put(dependentResource, reconcileResult);
      reconciled.add(dependentResourceNode);

      boolean ready = isConditionMet(dependentResourceNode.getReadyPostcondition(),
          dependentResource);
      if (ready) {
        log.debug("Setting already reconciled for: {}", dependentResourceNode);
        markAsVisited(dependentResourceNode);
        handleDependentsReconcile(dependentResourceNode);
      } else {
        setAlreadyReconciledButNotReady(dependentResourceNode);
      }
    }
  }

  private class NodeDeleteExecutor<R> extends NodeExecutor<R, P> {

    private NodeDeleteExecutor(DependentResourceNode<R, P> dependentResourceNode) {
      super(dependentResourceNode, WorkflowReconcileExecutor.this);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doRun(DependentResourceNode<R, P> dependentResourceNode,
        DependentResource<R, P> dependentResource) {
      var deletePostCondition = dependentResourceNode.getDeletePostcondition();

      if (dependentResource.isDeletable()) {
        ((Deleter<P>) dependentResource).delete(primary, context);
      }
      boolean deletePostConditionMet = isConditionMet(deletePostCondition, dependentResource);
      if (deletePostConditionMet) {
        markAsVisited(dependentResourceNode);
        handleDependentDeleted(dependentResourceNode);
      } else {
        // updating alreadyVisited needs to be the last operation otherwise could lead to a race
        // condition in handleDelete condition checks
        deletePostConditionNotMet.add(dependentResourceNode);
        markAsVisited(dependentResourceNode);
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

  private synchronized void handleDependentsReconcile(
      DependentResourceNode<?, P> dependentResourceNode) {
    var dependents = dependentResourceNode.getParents();
    dependents.forEach(d -> {
      log.debug("Handle reconcile for dependent: {} of parent:{}", d, dependentResourceNode);
      handleReconcile(d);
    });
  }


  private void handleReconcileConditionNotMet(DependentResourceNode<?, P> dependentResourceNode) {
    Set<DependentResourceNode> bottomNodes = new HashSet<>();
    markDependentsForDelete(dependentResourceNode, bottomNodes);
    bottomNodes.forEach(this::handleDelete);
  }

  private void markDependentsForDelete(DependentResourceNode<?, P> dependentResourceNode,
      Set<DependentResourceNode> bottomNodes) {
    markedForDelete.add(dependentResourceNode);
    var dependents = dependentResourceNode.getParents();
    if (dependents.isEmpty()) {
      bottomNodes.add(dependentResourceNode);
    } else {
      dependents.forEach(d -> markDependentsForDelete(d, bottomNodes));
    }
  }

  private boolean allParentsReconciledAndReady(DependentResourceNode<?, ?> dependentResourceNode) {
    return dependentResourceNode.getDependsOn().isEmpty()
        || dependentResourceNode.getDependsOn().stream()
            .allMatch(d -> alreadyVisited(d) && !notReady.contains(d));
  }

  private boolean hasErroredParent(DependentResourceNode<?, ?> dependentResourceNode) {
    return !dependentResourceNode.getDependsOn().isEmpty()
        && dependentResourceNode.getDependsOn().stream()
            .anyMatch(this::isInError);
  }

  private WorkflowReconcileResult createReconcileResult() {
    return new WorkflowReconcileResult(
        reconciled.stream()
            .map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toList()),
        notReady.stream()
            .map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toList()),
        getErroredDependents(),
        reconcileResults);
  }

}
