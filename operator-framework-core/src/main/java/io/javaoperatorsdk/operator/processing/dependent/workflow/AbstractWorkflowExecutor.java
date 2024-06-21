package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings("rawtypes")
abstract class AbstractWorkflowExecutor<P extends HasMetadata> {

  protected final DefaultWorkflow<P> workflow;
  protected final P primary;
  protected final ResourceID primaryID;
  protected final Context<P> context;
  protected final Map<DependentResourceNode<?, P>, WorkflowResult.DetailBuilder<?>> results;
  /**
   * Covers both deleted and reconciled
   */
  private final Map<DependentResourceNode, Future<?>> actualExecutions = new ConcurrentHashMap<>();
  private final ExecutorService executorService;

  protected AbstractWorkflowExecutor(DefaultWorkflow<P> workflow, P primary, Context<P> context) {
    this.workflow = workflow;
    this.primary = primary;
    this.context = context;
    this.primaryID = ResourceID.fromResource(primary);
    executorService = context.getWorkflowExecutorService();
    results = new ConcurrentHashMap<>(workflow.getDependentResourcesByName().size());
  }

  protected abstract Logger logger();

  protected synchronized void waitForScheduledExecutionsToRun() {
    // in case when workflow just contains non-activated dependents,
    // it needs to be checked first if there are already no executions
    // scheduled at the beginning.
    if (noMoreExecutionsScheduled()) {
      return;
    }
    while (true) {
      try {
        this.wait();
        if (noMoreExecutionsScheduled()) {
          break;
        } else {
          logger().warn("Notified but still resources under execution. This should not happen.");
        }
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
    return getResultFlagFor(dependentResourceNode, WorkflowResult.DetailBuilder::isVisited);
  }

  protected boolean postDeleteConditionNotMet(DependentResourceNode<?, P> drn) {
    return getResultFlagFor(drn, WorkflowResult.DetailBuilder::hasPostDeleteConditionNotMet);
  }

  protected boolean isMarkedForDelete(DependentResourceNode<?, P> drn) {
    return getResultFlagFor(drn, WorkflowResult.DetailBuilder::isMarkedForDelete);
  }

  protected WorkflowResult.DetailBuilder createOrGetResultFor(
      DependentResourceNode<?, P> dependentResourceNode) {
    return results.computeIfAbsent(dependentResourceNode,
        unused -> new WorkflowResult.DetailBuilder());
  }

  protected Optional<WorkflowResult.DetailBuilder<?>> getResultFor(
      DependentResourceNode<?, P> dependentResourceNode) {
    return Optional.ofNullable(results.get(dependentResourceNode));
  }

  protected boolean getResultFlagFor(DependentResourceNode<?, P> dependentResourceNode,
      Function<WorkflowResult.DetailBuilder<?>, Boolean> flag) {
    return getResultFor(dependentResourceNode).map(flag).orElse(false);
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
    createOrGetResultFor(dependentResourceNode).withError(e);
  }

  protected boolean isNotReady(DependentResourceNode<?, P> dependentResourceNode) {
    return getResultFlagFor(dependentResourceNode, WorkflowResult.DetailBuilder::isNotReady);
  }

  protected boolean isInError(DependentResourceNode<?, P> dependentResourceNode) {
   return getResultFlagFor(dependentResourceNode, WorkflowResult.DetailBuilder::hasError);
  }

  protected synchronized void handleNodeExecutionFinish(
      DependentResourceNode<?, P> dependentResourceNode) {
    logger().trace("Finished execution for: {} primary: {}", dependentResourceNode, primaryID);
    actualExecutions.remove(dependentResourceNode);
    if (noMoreExecutionsScheduled()) {
      this.notifyAll();
    }
  }

  @SuppressWarnings("unchecked")
  protected <R> boolean isConditionMet(
      Optional<DependentResourceNode.ConditionWithType<R, P, ?>> condition,
      DependentResourceNode<R, P> dependentResource) {
    final var dr = dependentResource.getDependentResource();
    return condition.map(c -> {
      final ResultCondition.Result<?> r = c.detailedIsMet(dr, primary, context);
      results.computeIfAbsent(dependentResource, unused -> new WorkflowResult.DetailBuilder())
          .withResultForCondition(c, r);
      return r;
    }).orElse(ResultCondition.Result.metWithoutResult).isSuccess();
  }

  protected <R> void submit(DependentResourceNode<R, P> dependentResourceNode,
      NodeExecutor<R, P> nodeExecutor, String operation) {
    final Future<?> future = executorService.submit(nodeExecutor);
    markAsExecuting(dependentResourceNode, future);
    logger().debug("Submitted to {}: {} primaryID: {}", operation, dependentResourceNode,
        primaryID);
  }

  protected <R> void registerOrDeregisterEventSourceBasedOnActivation(
      boolean activationConditionMet,
      DependentResourceNode<R, P> dependentResourceNode) {
    if (dependentResourceNode.getActivationCondition().isPresent()) {
      final var dr = dependentResourceNode.getDependentResource();
      final var eventSourceRetriever = context.eventSourceRetriever();
      var eventSource =
          dr.eventSource(eventSourceRetriever.eventSourceContextForDynamicRegistration());
      if (activationConditionMet) {
        var es = eventSource.orElseThrow();
        eventSourceRetriever.dynamicallyRegisterEventSource(es);
      } else {
        eventSourceRetriever.dynamicallyDeRegisterEventSource(eventSource.orElseThrow().name());
      }
    }
  }

  protected Map<DependentResource, WorkflowResult.Detail<?>> asDetails() {
    return results.entrySet().stream()
        .collect(
            Collectors.toMap(e -> e.getKey().getDependentResource(), e -> e.getValue().build()));
  }
}
