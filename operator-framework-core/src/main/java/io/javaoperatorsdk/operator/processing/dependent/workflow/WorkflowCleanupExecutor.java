package io.javaoperatorsdk.operator.processing.dependent.workflow;

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
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

@SuppressWarnings("rawtypes")
public class WorkflowCleanupExecutor<P extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(WorkflowCleanupExecutor.class);

  private final Map<DependentResourceNode, Future<?>> actualExecutions =
      new ConcurrentHashMap<>();
  private final Map<DependentResourceNode, Exception> exceptionsDuringExecution =
      new ConcurrentHashMap<>();
  private final Set<DependentResourceNode> alreadyVisited = ConcurrentHashMap.newKeySet();
  private final Set<DependentResourceNode> postDeleteConditionNotMet =
      ConcurrentHashMap.newKeySet();
  private final Set<DependentResourceNode> deleteCalled = ConcurrentHashMap.newKeySet();

  private final Workflow<P> workflow;
  private final P primary;
  private final Context<P> context;

  public WorkflowCleanupExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.workflow = workflow;
    this.primary = primary;
    this.context = context;
  }

  public synchronized WorkflowCleanupResult cleanup() {
    for (DependentResourceNode dependentResourceNode : workflow
        .getBottomLevelResource()) {
      handleCleanup(dependentResourceNode);
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
    return createCleanupResult();
  }

  private synchronized boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty();
  }

  private synchronized void handleCleanup(DependentResourceNode dependentResourceNode) {
    log.debug("Submitting for cleanup: {}", dependentResourceNode);

    if (alreadyVisited(dependentResourceNode)
        || isCleaningNow(dependentResourceNode)
        || !allDependentsCleaned(dependentResourceNode)
        || hasErroredDependent(dependentResourceNode)) {
      log.debug("Skipping submit of: {}, ", dependentResourceNode);
      return;
    }

    Future<?> nodeFuture =
        workflow.getExecutorService().submit(
            new NodeExecutor(dependentResourceNode));
    actualExecutions.put(dependentResourceNode, nodeFuture);
    log.debug("Submitted for cleanup: {}", dependentResourceNode);
  }

  private class NodeExecutor implements Runnable {

    private final DependentResourceNode<?, P> dependentResourceNode;

    private NodeExecutor(DependentResourceNode<?, P> dependentResourceNode) {
      this.dependentResourceNode = dependentResourceNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        var dependentResource = dependentResourceNode.getDependentResource();
        var deletePostCondition = dependentResourceNode.getDeletePostCondition();

        if (dependentResource instanceof Deleter
            && !(dependentResource instanceof GarbageCollected)) {
          ((Deleter<P>) dependentResourceNode.getDependentResource()).delete(primary, context);
          deleteCalled.add(dependentResourceNode);
        }
        alreadyVisited.add(dependentResourceNode);
        boolean deletePostConditionMet =
            deletePostCondition.map(c -> c.isMet(dependentResource, primary, context)).orElse(true);
        if (deletePostConditionMet) {
          handleDependentCleaned(dependentResourceNode);
        } else {
          postDeleteConditionNotMet.add(dependentResourceNode);
        }
      } catch (RuntimeException e) {
        handleExceptionInExecutor(dependentResourceNode, e);
      } finally {
        handleNodeExecutionFinish(dependentResourceNode);
      }
    }
  }

  private synchronized void handleDependentCleaned(
      DependentResourceNode<?, P> dependentResourceNode) {
    var dependOns = dependentResourceNode.getDependsOn();
    if (dependOns != null) {
      dependOns.forEach(d -> {
        log.debug("Handle cleanup for dependent: {} of parent:{}", d, dependentResourceNode);
        handleCleanup(d);
      });
    }
  }

  private synchronized void handleExceptionInExecutor(
      DependentResourceNode<?, P> dependentResourceNode,
      RuntimeException e) {
    exceptionsDuringExecution.put(dependentResourceNode, e);
  }

  private synchronized void handleNodeExecutionFinish(
      DependentResourceNode<?, P> dependentResourceNode) {
    log.debug("Finished execution for: {}", dependentResourceNode);
    actualExecutions.remove(dependentResourceNode);
    if (actualExecutions.isEmpty()) {
      this.notifyAll();
    }
  }

  private boolean isCleaningNow(DependentResourceNode<?, ?> dependentResourceNode) {
    return actualExecutions.containsKey(dependentResourceNode);
  }

  private boolean alreadyVisited(
      DependentResourceNode<?, ?> dependentResourceNode) {
    return alreadyVisited.contains(dependentResourceNode);
  }

  private boolean allDependentsCleaned(
      DependentResourceNode<?, P> dependentResourceNode) {
    var parents = dependentResourceNode.getParents();
    return parents.isEmpty()
        || parents.stream()
            .allMatch(d -> alreadyVisited(d) && !postDeleteConditionNotMet.contains(d));
  }

  private boolean hasErroredDependent(
      DependentResourceNode<?, P> dependentResourceNode) {
    var parents = dependentResourceNode.getParents();
    return !parents.isEmpty()
        && parents.stream().anyMatch(exceptionsDuringExecution::containsKey);
  }

  private WorkflowCleanupResult createCleanupResult() {
    var result = new WorkflowCleanupResult();
    result.setErroredDependents(exceptionsDuringExecution
        .entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().getDependentResource(), Map.Entry::getValue)));

    result.setPostConditionNotMetDependents(
        postDeleteConditionNotMet.stream().map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toList()));
    result.setDeleteCalledOnDependents(
        deleteCalled.stream().map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toList()));
    return result;
  }
}
