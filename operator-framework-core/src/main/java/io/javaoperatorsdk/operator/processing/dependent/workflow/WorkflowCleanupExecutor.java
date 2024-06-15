package io.javaoperatorsdk.operator.processing.dependent.workflow;

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
    for (DependentResourceNode dependentResourceNode : workflow.getBottomLevelResource()) {
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
    log.debug("Submitting for cleanup: {} primaryID: {}", dependentResourceNode, primaryID);

    if (alreadyVisited(dependentResourceNode)
        || isExecutingNow(dependentResourceNode)
        || !allDependentsCleaned(dependentResourceNode)
        || hasErroredDependent(dependentResourceNode)) {
      log.debug("Skipping submit of: {} primaryID: {}", dependentResourceNode, primaryID);
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
          createOrGetResultFor(dependentResourceNode).withDeleted(true);
        }

        deletePostConditionMet =
            isConditionMet(dependentResourceNode.getDeletePostcondition(), dependentResourceNode);
      }

      if (deletePostConditionMet) {
        createOrGetResultFor(dependentResourceNode).markAsVisited();
        handleDependentCleaned(dependentResourceNode);
      }
    }
  }

  private synchronized void handleDependentCleaned(
      DependentResourceNode<?, P> dependentResourceNode) {
    var dependOns = dependentResourceNode.getDependsOn();
    if (dependOns != null) {
      dependOns.forEach(d -> {
        log.debug("Handle cleanup for dependent: {} of parent: {} primaryID: {}", d,
            dependentResourceNode, primaryID);
        handleCleanup(d);
      });
    }
  }

  @SuppressWarnings("unchecked")
  private boolean allDependentsCleaned(DependentResourceNode dependentResourceNode) {
    List<DependentResourceNode> parents = dependentResourceNode.getParents();
    return parents.isEmpty()
        || parents.stream()
            .allMatch(d -> alreadyVisited(d) && postDeleteConditionNotMet(d));
  }

  @SuppressWarnings("unchecked")
  private boolean hasErroredDependent(DependentResourceNode dependentResourceNode) {
    List<DependentResourceNode> parents = dependentResourceNode.getParents();
    return !parents.isEmpty() && parents.stream().anyMatch(this::isInError);
  }

  private WorkflowCleanupResult createCleanupResult() {
    return new WorkflowCleanupResult(asDetails());
  }
}
