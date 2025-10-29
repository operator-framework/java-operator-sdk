/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
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
  protected final Map<DependentResourceNode<?, P>, BaseWorkflowResult.DetailBuilder<?>> results;

  /** Covers both deleted and reconciled */
  private final Map<DependentResourceNode, Future<?>> actualExecutions = new ConcurrentHashMap<>();

  private final ExecutorService executorService;

  protected AbstractWorkflowExecutor(DefaultWorkflow<P> workflow, P primary, Context<P> context) {
    this.workflow = workflow;
    this.primary = primary;
    this.context = context;
    this.primaryID = ResourceID.fromResource(primary);
    executorService = context.getWorkflowExecutorService();
    results = new HashMap<>(workflow.getDependentResourcesByName().size());
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
    return getResultFlagFor(dependentResourceNode, BaseWorkflowResult.DetailBuilder::isVisited);
  }

  protected boolean postDeleteConditionNotMet(DependentResourceNode<?, P> drn) {
    return getResultFlagFor(drn, BaseWorkflowResult.DetailBuilder::hasPostDeleteConditionNotMet);
  }

  protected boolean isMarkedForDelete(DependentResourceNode<?, P> drn) {
    return getResultFlagFor(drn, BaseWorkflowResult.DetailBuilder::isMarkedForDelete);
  }

  protected synchronized BaseWorkflowResult.DetailBuilder createOrGetResultFor(
      DependentResourceNode<?, P> dependentResourceNode) {
    return results.computeIfAbsent(
        dependentResourceNode, unused -> new BaseWorkflowResult.DetailBuilder());
  }

  protected synchronized Optional<BaseWorkflowResult.DetailBuilder<?>> getResultFor(
      DependentResourceNode<?, P> dependentResourceNode) {
    return Optional.ofNullable(results.get(dependentResourceNode));
  }

  protected boolean getResultFlagFor(
      DependentResourceNode<?, P> dependentResourceNode,
      Function<BaseWorkflowResult.DetailBuilder<?>, Boolean> flag) {
    return getResultFor(dependentResourceNode).map(flag).orElse(false);
  }

  protected boolean isExecutingNow(DependentResourceNode<?, P> dependentResourceNode) {
    return actualExecutions.containsKey(dependentResourceNode);
  }

  protected void markAsExecuting(
      DependentResourceNode<?, P> dependentResourceNode, Future<?> future) {
    actualExecutions.put(dependentResourceNode, future);
  }

  // Exception is required because of Kotlin
  protected synchronized void handleExceptionInExecutor(
      DependentResourceNode<?, P> dependentResourceNode, Exception e) {
    createOrGetResultFor(dependentResourceNode).withError(e);
  }

  protected boolean isReady(DependentResourceNode<?, P> dependentResourceNode) {
    return getResultFlagFor(dependentResourceNode, BaseWorkflowResult.DetailBuilder::isReady);
  }

  protected boolean isInError(DependentResourceNode<?, P> dependentResourceNode) {
    return getResultFlagFor(dependentResourceNode, BaseWorkflowResult.DetailBuilder::hasError);
  }

  protected synchronized void handleNodeExecutionFinish(
      DependentResourceNode<?, P> dependentResourceNode) {
    logger().trace("Finished execution for: {} primary: {}", dependentResourceNode, primaryID);
    actualExecutions.remove(dependentResourceNode);
    if (noMoreExecutionsScheduled()) {
      this.notifyAll();
    }
  }

  @SuppressWarnings({"unchecked", "OptionalUsedAsFieldOrParameterType"})
  protected <R> boolean isConditionMet(
      Optional<ConditionWithType<R, P, ?>> condition,
      DependentResourceNode<R, P> dependentResource) {
    final var dr = dependentResource.getDependentResource();
    return condition
        .map(
            c -> {
              final DetailedCondition.Result<?> r = c.detailedIsMet(dr, primary, context);
              synchronized (this) {
                results
                    .computeIfAbsent(
                        dependentResource, unused -> new BaseWorkflowResult.DetailBuilder())
                    .withResultForCondition(c, r);
              }
              return r;
            })
        .orElse(DetailedCondition.Result.metWithoutResult)
        .isSuccess();
  }

  protected <R> void submit(
      DependentResourceNode<R, P> dependentResourceNode,
      NodeExecutor<R, P> nodeExecutor,
      String operation) {
    final Future<?> future = executorService.submit(nodeExecutor);
    markAsExecuting(dependentResourceNode, future);
    logger()
        .debug("Submitted to {}: {} primaryID: {}", operation, dependentResourceNode, primaryID);
  }

  protected <R> void registerOrDeregisterEventSourceBasedOnActivation(
      boolean activationConditionMet, DependentResourceNode<R, P> dependentResourceNode) {
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

  protected synchronized Map<DependentResource, BaseWorkflowResult.Detail<?>> asDetails() {
    return results.entrySet().stream()
        .collect(
            Collectors.toMap(e -> e.getKey().getDependentResource(), e -> e.getValue().build()));
  }
}
