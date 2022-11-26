package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public abstract class AbstractWorkflowExecutor<P extends HasMetadata> {

  protected final Workflow<P> workflow;
  protected final P primary;
  protected final Context<P> context;
  /**
   * Covers both deleted and reconciled
   */
  private final Set<DependentResourceNode> alreadyVisited = ConcurrentHashMap.newKeySet();
  private final Map<DependentResourceNode, Future<?>> actualExecutions = new HashMap<>();
  private final Map<DependentResourceNode, Exception> exceptionsDuringExecution =
      new ConcurrentHashMap<>();

  public AbstractWorkflowExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.workflow = workflow;
    this.primary = primary;
    this.context = context;
  }

  protected abstract Logger logger();

  protected void waitForScheduledExecutionsToRun() {
    while (true) {
      try {
        this.wait();
        if (noMoreExecutionsScheduled()) {
          break;
        } else {
          logger().warn("Notified but still resources under execution. This should not happen.");
        }
      } catch (InterruptedException e) {
        logger().warn("Thread interrupted", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  protected boolean noMoreExecutionsScheduled() {
    return actualExecutions.isEmpty();
  }

  protected boolean alreadyVisited(DependentResourceNode<?, P> dependentResourceNode) {
    return alreadyVisited.contains(dependentResourceNode);
  }

  protected void markAsVisited(DependentResourceNode<?, P> dependentResourceNode) {
    alreadyVisited.add(dependentResourceNode);
  }

  protected boolean isExecutingNow(DependentResourceNode<?, P> dependentResourceNode) {
    return actualExecutions.containsKey(dependentResourceNode);
  }

  protected void markAsExecuting(DependentResourceNode<?, P> dependentResourceNode,
      Future<?> future) {
    actualExecutions.put(dependentResourceNode, future);
  }

  protected synchronized void handleExceptionInExecutor(
      DependentResourceNode<?, P> dependentResourceNode,
      RuntimeException e) {
    exceptionsDuringExecution.put(dependentResourceNode, e);
  }

  protected boolean isInError(DependentResourceNode<?, P> dependentResourceNode) {
    return exceptionsDuringExecution.containsKey(dependentResourceNode);
  }

  protected Map<DependentResource, Exception> getErroredDependents() {
    return exceptionsDuringExecution.entrySet().stream()
        .collect(
            Collectors.toMap(e -> workflow.getDependentResourceFor(e.getKey()), Entry::getValue));
  }

  protected synchronized void handleNodeExecutionFinish(
      DependentResourceNode<?, P> dependentResourceNode) {
    logger().debug("Finished execution for: {}", dependentResourceNode);
    actualExecutions.remove(dependentResourceNode);
    if (noMoreExecutionsScheduled()) {
      this.notifyAll();
    }
  }

  @SuppressWarnings("unchecked")
  protected <R> DependentResource<R, P> getDependentResourceFor(DependentResourceNode<R, P> drn) {
    return (DependentResource<R, P>) workflow.getDependentResourceFor(drn);
  }

  protected <R> boolean isConditionMet(Optional<Condition<R, P>> condition,
      DependentResource<R, P> dependentResource) {
    return condition.map(c -> c.isMet(primary,
            dependentResource.getSecondaryResource(primary, context).orElse(null),
            context))
        .orElse(true);
  }
}
