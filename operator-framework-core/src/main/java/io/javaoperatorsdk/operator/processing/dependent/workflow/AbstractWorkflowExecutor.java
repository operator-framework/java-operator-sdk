package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings("rawtypes")
public abstract class AbstractWorkflowExecutor<P extends HasMetadata> {

  protected final Workflow<P> workflow;
  protected final P primary;
  protected final ResourceID primaryID;
  protected final Context<P> context;
  /**
   * Covers both deleted and reconciled
   */
  private final Set<DependentResourceNode> alreadyVisited = ConcurrentHashMap.newKeySet();
  private final Map<DependentResourceNode, Future<?>> actualExecutions = new ConcurrentHashMap<>();
  private final Map<DependentResourceNode, Exception> exceptionsDuringExecution =
      new ConcurrentHashMap<>();
  private final ExecutorService executorService;

  public AbstractWorkflowExecutor(Workflow<P> workflow, P primary, Context<P> context) {
    this.workflow = workflow;
    this.primary = primary;
    this.context = context;
    this.primaryID = ResourceID.fromResource(primary);
    executorService = context.getWorkflowExecutorService();
  }

  protected abstract Logger logger();

  protected synchronized void waitForScheduledExecutionsToRun() {
    while (true) {
      try {
        // in case when workflow just contains non-activated dependents,
        // it needs to be checked first if there are already no executions
        // scheduled at the beginning.
        if (noMoreExecutionsScheduled()) {
          break;
        } else {
          logger().warn("Notified but still resources under execution. This should not happen.");
        }
        this.wait();
      } catch (InterruptedException e) {
        if (noMoreExecutionsScheduled()) {
          logger().debug("interrupted, no more executions for: {}", primaryID);
          return;
        } else {
          logger().error("Thread interrupted for primary: {}", primaryID, e);
          throw new OperatorException(e);
        }
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
            Collectors.toMap(e -> e.getKey().getDependentResource(), Entry::getValue));
  }

  protected synchronized void handleNodeExecutionFinish(
      DependentResourceNode<?, P> dependentResourceNode) {
    logger().trace("Finished execution for: {} primary: {}", dependentResourceNode, primaryID);
    actualExecutions.remove(dependentResourceNode);
    if (noMoreExecutionsScheduled()) {
      this.notifyAll();
    }
  }

  protected <R> boolean isConditionMet(Optional<Condition<R, P>> condition,
      DependentResource<R, P> dependentResource) {
    return condition.map(c -> c.isMet(dependentResource, primary, context)).orElse(true);
  }

  protected <R> void submit(DependentResourceNode<R, P> dependentResourceNode,
      NodeExecutor<R, P> nodeExecutor, String operation) {
    final Future<?> future = executorService.submit(nodeExecutor);
    markAsExecuting(dependentResourceNode, future);
    logger().debug("Submitted to {}: {} primaryID: {}", operation, dependentResourceNode,
        primaryID);
  }
}
