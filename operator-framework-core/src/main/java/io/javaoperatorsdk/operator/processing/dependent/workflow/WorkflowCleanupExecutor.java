package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;

@SuppressWarnings("rawtypes")
class WorkflowCleanupExecutor<P extends HasMetadata> extends AbstractWorkflowExecutor<P> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowCleanupExecutor.class);
  private static final String CLEANUP = "cleanup";

  WorkflowCleanupExecutor(DefaultWorkflow<P> workflow, P primary, Context<P> context) {
    super(workflow, primary, context);
  }

  public synchronized WorkflowCleanupResult cleanup() {
    for (DependentResourceNode dependentResourceNode :
        workflow.getBottomLevelDependentResources()) {
      handleCleanup(dependentResourceNode);
    }
    waitForScheduledExecutionsToRun();
    return createCleanupResult();
  }

  @Override
  protected Logger logger() {
    return log;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private synchronized void handleCleanup(DependentResourceNode dependentResourceNode) {
    log.debug("Considering for cleanup: {} primaryID: {}", dependentResourceNode, primaryID);

    final var alreadyVisited = alreadyVisited(dependentResourceNode);
    final var executingNow = isExecutingNow(dependentResourceNode);
    final var waitingOnDependents = !allDependentsCleaned(dependentResourceNode);
    final var hasErroredDependent = hasErroredDependent(dependentResourceNode);
    if (waitingOnDependents || alreadyVisited || executingNow || hasErroredDependent) {
      if (log.isDebugEnabled()) {
        final var causes = new ArrayList<String>();
        if (alreadyVisited) {
          causes.add("already visited");
        }
        if (executingNow) {
          causes.add("executing now");
        }
        if (waitingOnDependents) {
          causes.add("waiting on dependents");
        }
        if (hasErroredDependent) {
          causes.add("errored dependent");
        }
        log.debug(
            "Skipping: {} primaryID: {} causes: {}",
            dependentResourceNode,
            primaryID,
            String.join(", ", causes));
      }
      return;
    }

    submit(dependentResourceNode, new CleanupExecutor<>(dependentResourceNode), CLEANUP);
  }

  private class CleanupExecutor<R> extends NodeExecutor<R, P> {

    private CleanupExecutor(DependentResourceNode<R, P> drn) {
      super(drn, WorkflowCleanupExecutor.this);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doRun(DependentResourceNode<R, P> dependentResourceNode) {
      final var active =
          isConditionMet(dependentResourceNode.getActivationCondition(), dependentResourceNode);
      registerOrDeregisterEventSourceBasedOnActivation(active, dependentResourceNode);

      boolean deletePostConditionMet = true;
      if (active) {
        final var dependentResource = dependentResourceNode.getDependentResource();
        if (dependentResource.isDeletable()) {
          ((Deleter<P>) dependentResource).delete(primary, context);
          createOrGetResultFor(dependentResourceNode).markAsDeleted();
        }

        deletePostConditionMet =
            isConditionMet(dependentResourceNode.getDeletePostcondition(), dependentResourceNode);
      }

      createOrGetResultFor(dependentResourceNode).markAsVisited();

      if (deletePostConditionMet) {
        handleDependentCleaned(dependentResourceNode);
      }
    }
  }

  private synchronized void handleDependentCleaned(
      DependentResourceNode<?, P> dependentResourceNode) {
    var dependOns = dependentResourceNode.getDependsOn();
    if (dependOns != null) {
      dependOns.forEach(
          d -> {
            log.debug(
                "Handle cleanup for dependent: {} of parent: {} primaryID: {}",
                d,
                dependentResourceNode,
                primaryID);
            handleCleanup(d);
          });
    }
  }

  @SuppressWarnings("unchecked")
  private boolean allDependentsCleaned(DependentResourceNode dependentResourceNode) {
    List<DependentResourceNode> parents = dependentResourceNode.getParents();
    return parents.isEmpty()
        || parents.stream().allMatch(d -> alreadyVisited(d) && !postDeleteConditionNotMet(d));
  }

  @SuppressWarnings("unchecked")
  private boolean hasErroredDependent(DependentResourceNode dependentResourceNode) {
    List<DependentResourceNode> parents = dependentResourceNode.getParents();
    return !parents.isEmpty() && parents.stream().anyMatch(this::isInError);
  }

  private WorkflowCleanupResult createCleanupResult() {
    return new DefaultWorkflowCleanupResult(asDetails());
  }
}
