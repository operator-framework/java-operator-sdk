package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

@SuppressWarnings({"rawtypes", "unchecked"})
class WorkflowReconcileExecutor<P extends HasMetadata> extends AbstractWorkflowExecutor<P> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);
  private static final String RECONCILE = "reconcile";
  private static final String DELETE = "delete";

  private final Set<DependentResourceNode> markedForDelete;

  public WorkflowReconcileExecutor(DefaultWorkflow<P> workflow, P primary, Context<P> context) {
    super(workflow, primary, context);
    final var size = workflow.size();
    markedForDelete = ConcurrentHashMap.newKeySet(size);
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
    log.debug("Submitting for reconcile: {} primaryID: {}", dependentResourceNode, primaryID);

    if (alreadyVisited(dependentResourceNode)
        || isExecutingNow(dependentResourceNode)
        || !allParentsReconciledAndReady(dependentResourceNode)
        || markedForDelete.contains(dependentResourceNode)
        || hasErroredParent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, primaryID: {}", dependentResourceNode, primaryID);
      return;
    }

    boolean activationConditionMet = isConditionMet(dependentResourceNode.getActivationCondition(),
        dependentResourceNode);
    registerOrDeregisterEventSourceBasedOnActivation(activationConditionMet, dependentResourceNode);

    boolean reconcileConditionMet = true;
    if (activationConditionMet) {
      reconcileConditionMet = isConditionMet(dependentResourceNode.getReconcilePrecondition(),
          dependentResourceNode);
    }
    if (!reconcileConditionMet || !activationConditionMet) {
      handleReconcileOrActivationConditionNotMet(dependentResourceNode, activationConditionMet);
    } else {
      submit(dependentResourceNode, new NodeReconcileExecutor<>(dependentResourceNode), RECONCILE);
    }
  }

  private synchronized void handleDelete(DependentResourceNode dependentResourceNode) {
    log.debug("Submitting for delete: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isExecutingNow(dependentResourceNode)
        || !markedForDelete.contains(dependentResourceNode)
        || !allDependentsDeletedAlready(dependentResourceNode)) {
      log.debug("Skipping submit for delete of: {} primaryID: {} ", dependentResourceNode,
          primaryID);
      return;
    }

    submit(dependentResourceNode,
        new NodeDeleteExecutor<>(dependentResourceNode), DELETE);
  }

  private boolean allDependentsDeletedAlready(DependentResourceNode<?, P> dependentResourceNode) {
    var dependents = dependentResourceNode.getParents();
    return dependents.stream().allMatch(d -> alreadyVisited(d) && !isNotReady(d)
        && !isInError(d) && !postDeleteConditionNotMet(d));
  }

  private class NodeReconcileExecutor<R> extends NodeExecutor<R, P> {

    private NodeReconcileExecutor(DependentResourceNode<R, P> dependentResourceNode) {
      super(dependentResourceNode, WorkflowReconcileExecutor.this);
    }

    @Override
    protected void doRun(DependentResourceNode<R, P> dependentResourceNode) {
      final var dependentResource = dependentResourceNode.getDependentResource();
      log.debug(
          "Reconciling for primary: {} node: {} ", primaryID, dependentResourceNode);
      ReconcileResult reconcileResult = dependentResource.reconcile(primary, context);
      createOrGetResultFor(dependentResourceNode).withReconcileResult(reconcileResult);

      if (isConditionMet(dependentResourceNode.getReconcilePrecondition(), dependentResourceNode)) {
        log.debug("Setting already reconciled for: {} primaryID: {}",
            dependentResourceNode, primaryID);
        markAsVisited(dependentResourceNode);
        handleDependentsReconcile(dependentResourceNode);
      } else {
        log.debug("Setting already reconciled but not ready for: {}", dependentResourceNode);
      }
    }
  }

  private class NodeDeleteExecutor<R> extends NodeExecutor<R, P> {

    private NodeDeleteExecutor(DependentResourceNode<R, P> dependentResourceNode) {
      super(dependentResourceNode, WorkflowReconcileExecutor.this);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doRun(DependentResourceNode<R, P> dependentResourceNode) {
      var deletePostCondition = dependentResourceNode.getDeletePostcondition();

      final var dependentResource = dependentResourceNode.getDependentResource();
      boolean deletePostConditionMet = true;
      if (isConditionMet(dependentResourceNode.getActivationCondition(), dependentResourceNode)) {
        // GarbageCollected status is irrelevant here, as this method is only called when a
        // precondition does not hold,
        // a deleter should be deleted even if it is otherwise garbage collected
        if (dependentResource instanceof Deleter) {
          ((Deleter<P>) dependentResource).delete(primary, context);
        }
        deletePostConditionMet = isConditionMet(deletePostCondition, dependentResourceNode);
      }

      if (deletePostConditionMet) {
        markAsVisited(dependentResourceNode);
        handleDependentDeleted(dependentResourceNode);
      }
    }
  }

  private synchronized void handleDependentDeleted(
      DependentResourceNode<?, P> dependentResourceNode) {
    dependentResourceNode.getDependsOn().forEach(dr -> {
      log.debug("Handle deleted for: {} with dependent: {} primaryID: {}", dr,
          dependentResourceNode, primaryID);
      handleDelete(dr);
    });
  }

  private synchronized void handleDependentsReconcile(
      DependentResourceNode<?, P> dependentResourceNode) {
    var dependents = dependentResourceNode.getParents();
    dependents.forEach(d -> {
      log.debug("Handle reconcile for dependent: {} of parent:{} primaryID: {}", d,
          dependentResourceNode, primaryID);
      handleReconcile(d);
    });
  }

  private void handleReconcileOrActivationConditionNotMet(
      DependentResourceNode<?, P> dependentResourceNode,
      boolean activationConditionMet) {
    Set<DependentResourceNode> bottomNodes = new HashSet<>();
    markDependentsForDelete(dependentResourceNode, bottomNodes, activationConditionMet);
    bottomNodes.forEach(this::handleDelete);
  }

  private void markDependentsForDelete(DependentResourceNode<?, P> dependentResourceNode,
      Set<DependentResourceNode> bottomNodes, boolean activationConditionMet) {
    // this is a check so the activation condition is not evaluated twice,
    // so if the activation condition was false, this node is not meant to be deleted.
    var dependents = dependentResourceNode.getParents();
    if (activationConditionMet) {
      markedForDelete.add(dependentResourceNode);
      if (dependents.isEmpty()) {
        bottomNodes.add(dependentResourceNode);
      } else {
        dependents.forEach(d -> markDependentsForDelete(d, bottomNodes, true));
      }
    } else {
      // this is for an edge case when there is only one resource but that is not active
      markAsVisited(dependentResourceNode);
      if (dependents.isEmpty()) {
        handleNodeExecutionFinish(dependentResourceNode);
      } else {
        dependents.forEach(d -> markDependentsForDelete(d, bottomNodes, true));
      }
    }
  }

  private boolean allParentsReconciledAndReady(DependentResourceNode<?, ?> dependentResourceNode) {
    return dependentResourceNode.getDependsOn().isEmpty()
        || dependentResourceNode.getDependsOn().stream()
            .allMatch(d -> alreadyVisited(d) && !isNotReady(d));
  }

  private boolean hasErroredParent(DependentResourceNode<?, ?> dependentResourceNode) {
    return !dependentResourceNode.getDependsOn().isEmpty()
        && dependentResourceNode.getDependsOn().stream()
            .anyMatch(this::isInError);
  }

  private WorkflowReconcileResult createReconcileResult() {
    return new WorkflowReconcileResult(asDetails());
  }
}
