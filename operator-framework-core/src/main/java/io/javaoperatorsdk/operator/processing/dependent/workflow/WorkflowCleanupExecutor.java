package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowCleanupExecutor<P extends HasMetadata> extends AbstractWorkflowExecutor<P> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowCleanupExecutor.class);

  private final Set<DependentResourceNode> postDeleteConditionNotMet =
      ConcurrentHashMap.newKeySet();
  private final Set<DependentResourceNode> deleteCalled = ConcurrentHashMap.newKeySet();
  private final ExecutorService executorService;

  public WorkflowCleanupExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    super(workflow, primary, context);
    this.executorService = context.getWorkflowExecutorService();
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

    Future<?> nodeFuture = executorService
        .submit(new CleanupExecutor<>(dependentResourceNode));
    markAsExecuting(dependentResourceNode, nodeFuture);
    log.debug("Submitted for cleanup: {}", dependentResourceNode);
  }


  private class CleanupExecutor<R> extends NodeExecutor<R, P> {

    private CleanupExecutor(DependentResourceNode<R, P> drn) {
      super(drn, WorkflowCleanupExecutor.this);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doRun(DependentResourceNode<R, P> dependentResourceNode,
        DependentResource<R, P> dependentResource) {
      var deletePostCondition = dependentResourceNode.getDeletePostcondition();

      if (dependentResource.isDeletable()) {
        ((Deleter<P>) dependentResource).delete(primary, context);
        deleteCalled.add(dependentResourceNode);
      }
      boolean deletePostConditionMet = isConditionMet(deletePostCondition, dependentResource);
      if (deletePostConditionMet) {
        markAsVisited(dependentResourceNode);
        handleDependentCleaned(dependentResourceNode);
      } else {
        // updating alreadyVisited needs to be the last operation otherwise could lead to a race
        // condition in handleCleanup condition checks
        postDeleteConditionNotMet.add(dependentResourceNode);
        markAsVisited(dependentResourceNode);
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
            .allMatch(d -> alreadyVisited(d) && !postDeleteConditionNotMet.contains(d));
  }

  @SuppressWarnings("unchecked")
  private boolean hasErroredDependent(DependentResourceNode dependentResourceNode) {
    List<DependentResourceNode> parents = dependentResourceNode.getParents();
    return !parents.isEmpty() && parents.stream().anyMatch(this::isInError);
  }

  private WorkflowCleanupResult createCleanupResult() {
    final var erroredDependents = getErroredDependents();
    final var postConditionNotMet = postDeleteConditionNotMet.stream()
        .map(DependentResourceNode::getDependentResource)
        .collect(Collectors.toList());
    final var deleteCalled = this.deleteCalled.stream()
        .map(DependentResourceNode::getDependentResource)
        .collect(Collectors.toList());
    return new WorkflowCleanupResult(erroredDependents, postConditionNotMet, deleteCalled);
  }
}
