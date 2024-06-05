package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowReconcileExecutor<P extends HasMetadata> extends AbstractWorkflowExecutor<P> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutor.class);
  private static final String RECONCILE = "reconcile";
  private static final String DELETE = "delete";

  private final Map<DependentResourceNode, Object> notReady;
  private final Set<DependentResourceNode> markedForDelete;
  private final Set<DependentResourceNode> deletePostConditionNotMet;
  private final Map<DependentResource, ReconcileResult> reconcileResults;

  public WorkflowReconcileExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    super(workflow, primary, context);
    final var size = workflow.size();
    reconcileResults = new HashMap<>(size);
    notReady = new HashMap<>(size);
    markedForDelete = ConcurrentHashMap.newKeySet(size);
    deletePostConditionNotMet = ConcurrentHashMap.newKeySet(size);
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
        dependentResourceNode.getDependentResource());
    registerOrDeregisterEventSourceBasedOnActivation(activationConditionMet, dependentResourceNode);

    boolean reconcileConditionMet = true;
    if (activationConditionMet) {
      reconcileConditionMet = isConditionMet(dependentResourceNode.getReconcilePrecondition(),
          dependentResourceNode.getDependentResource());
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
    return dependents.stream().allMatch(d -> alreadyVisited(d) && !notReady.containsKey(d)
        && !isInError(d) && !deletePostConditionNotMet.contains(d));
  }

  // needs to be in one step
  private synchronized void setAlreadyReconciledButNotReady(
      DependentResourceNode<?, P> dependentResourceNode, Object result) {
    log.debug("Setting already reconciled but not ready for: {}", dependentResourceNode);
    markAsVisited(dependentResourceNode);
    notReady.put(dependentResourceNode, result == null ? ResultCondition.NULL : result);
  }

  private class NodeReconcileExecutor<R> extends NodeExecutor<R, P> {

    private NodeReconcileExecutor(DependentResourceNode<R, P> dependentResourceNode) {
      super(dependentResourceNode, WorkflowReconcileExecutor.this);
    }

    @Override
    protected void doRun(DependentResourceNode<R, P> dependentResourceNode,
        DependentResource<R, P> dependentResource) {

      log.debug(
          "Reconciling for primary: {} node: {} ", primaryID, dependentResourceNode);
      ReconcileResult reconcileResult = dependentResource.reconcile(primary, context);
      reconcileResults.put(dependentResource, reconcileResult);

      final Object[] result = new Object[1];
      final var shouldProceed = dependentResourceNode.getReadyPostcondition().map(rpCondition -> {
        if (rpCondition instanceof ResultCondition resultCondition) {
          final var detailed = resultCondition.detailedIsMet(dependentResource, primary, context);
          result[0] = detailed.getResult();
          return detailed.isSuccess();
        } else {
          return rpCondition.isMet(dependentResource, primary, context);
        }
      }).orElse(true);

      if (shouldProceed) {
        log.debug("Setting already reconciled for: {} primaryID: {}",
            dependentResourceNode, primaryID);
        markAsVisited(dependentResourceNode);
        handleDependentsReconcile(dependentResourceNode);
      } else {
        setAlreadyReconciledButNotReady(dependentResourceNode, result[0]);
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

      boolean deletePostConditionMet = true;
      if (isConditionMet(dependentResourceNode.getActivationCondition(), dependentResource)) {
        // GarbageCollected status is irrelevant here, as this method is only called when a
        // precondition does not hold,
        // a deleter should be deleted even if it is otherwise garbage collected
        if (dependentResource instanceof Deleter) {
          ((Deleter<P>) dependentResource).delete(primary, context);
        }
        deletePostConditionMet = isConditionMet(deletePostCondition, dependentResource);
      }

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
            .allMatch(d -> alreadyVisited(d) && !notReady.containsKey(d));
  }

  private boolean hasErroredParent(DependentResourceNode<?, ?> dependentResourceNode) {
    return !dependentResourceNode.getDependsOn().isEmpty()
        && dependentResourceNode.getDependsOn().stream()
            .anyMatch(this::isInError);
  }

  private WorkflowReconcileResult createReconcileResult() {
    return new WorkflowReconcileResult(
        notReady.entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey().getDependentResource(),
                Map.Entry::getValue)),
        getErroredDependents(),
        reconcileResults);
  }
}
