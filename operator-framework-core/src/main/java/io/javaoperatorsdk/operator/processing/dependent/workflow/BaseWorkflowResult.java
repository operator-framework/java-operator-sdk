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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

@SuppressWarnings("rawtypes")
class BaseWorkflowResult implements WorkflowResult {
  private final Map<DependentResource, Detail<?>> results;
  private Boolean hasErroredDependents;

  BaseWorkflowResult(Map<DependentResource, Detail<?>> results) {
    this.results = results;
  }

  @Override
  public Map<DependentResource, Exception> getErroredDependents() {
    return getErroredDependentsStream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().error));
  }

  private Stream<Entry<DependentResource, Detail<?>>> getErroredDependentsStream() {
    return results.entrySet().stream().filter(entry -> entry.getValue().error != null);
  }

  protected Map<DependentResource, Detail<?>> results() {
    return results;
  }

  @Override
  public Optional<DependentResource> getDependentResourceByName(String name) {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    return results.keySet().stream().filter(dr -> dr.name().equals(name)).findFirst();
  }

  @Override
  public <T> Optional<T> getDependentConditionResult(
      DependentResource dependentResource,
      Condition.Type conditionType,
      Class<T> expectedResultType) {
    if (dependentResource == null) {
      return Optional.empty();
    }

    final var result = new Object[1];
    try {
      return Optional.ofNullable(results().get(dependentResource))
          .flatMap(detail -> detail.getResultForConditionWithType(conditionType))
          .map(r -> result[0] = r.getDetail())
          .map(expectedResultType::cast);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Condition "
              + "result "
              + result[0]
              + " for Dependent "
              + dependentResource.name()
              + " doesn't match expected type "
              + expectedResultType.getSimpleName(),
          e);
    }
  }

  protected List<DependentResource> listFilteredBy(Function<Detail, Boolean> filter) {
    return results.entrySet().stream()
        .filter(e -> filter.apply(e.getValue()))
        .map(Map.Entry::getKey)
        .toList();
  }

  @Override
  public boolean erroredDependentsExist() {
    if (hasErroredDependents == null) {
      hasErroredDependents = !getErroredDependents().isEmpty();
    }
    return hasErroredDependents;
  }

  @Override
  public void throwAggregateExceptionIfErrorsPresent() {
    if (erroredDependentsExist()) {
      throw new AggregatedOperatorException(
          "Exception(s) during workflow execution.",
          getErroredDependentsStream()
              .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().error)));
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  static class DetailBuilder<R> {
    private Exception error;
    private ReconcileResult<R> reconcileResult;
    private DetailedCondition.Result activationConditionResult;
    private DetailedCondition.Result deletePostconditionResult;
    private DetailedCondition.Result readyPostconditionResult;
    private DetailedCondition.Result reconcilePostconditionResult;
    private boolean deleted;
    private boolean visited;
    private boolean markedForDelete;

    Detail<R> build() {
      return new Detail<>(
          error,
          reconcileResult,
          activationConditionResult,
          deletePostconditionResult,
          readyPostconditionResult,
          reconcilePostconditionResult,
          deleted,
          visited,
          markedForDelete);
    }

    DetailBuilder<R> withResultForCondition(
        ConditionWithType conditionWithType, DetailedCondition.Result conditionResult) {
      switch (conditionWithType.type()) {
        case ACTIVATION -> activationConditionResult = conditionResult;
        case DELETE -> deletePostconditionResult = conditionResult;
        case READY -> readyPostconditionResult = conditionResult;
        case RECONCILE -> reconcilePostconditionResult = conditionResult;
        default ->
            throw new IllegalStateException("Unexpected condition type: " + conditionWithType);
      }
      return this;
    }

    DetailBuilder<R> withError(Exception error) {
      this.error = error;
      return this;
    }

    DetailBuilder<R> withReconcileResult(ReconcileResult<R> reconcileResult) {
      this.reconcileResult = reconcileResult;
      return this;
    }

    DetailBuilder<R> markAsDeleted() {
      this.deleted = true;
      return this;
    }

    public boolean hasError() {
      return error != null;
    }

    public boolean hasPostDeleteConditionNotMet() {
      return deletePostconditionResult != null && !deletePostconditionResult.isSuccess();
    }

    public boolean isReady() {
      return readyPostconditionResult == null || readyPostconditionResult.isSuccess();
    }

    DetailBuilder<R> markAsVisited() {
      visited = true;
      return this;
    }

    public boolean isVisited() {
      return visited;
    }

    public boolean isMarkedForDelete() {
      return markedForDelete;
    }

    DetailBuilder<R> markForDelete() {
      markedForDelete = true;
      return this;
    }
  }

  record Detail<R>(
      Exception error,
      ReconcileResult<R> reconcileResult,
      DetailedCondition.Result activationConditionResult,
      DetailedCondition.Result deletePostconditionResult,
      DetailedCondition.Result readyPostconditionResult,
      DetailedCondition.Result reconcilePostconditionResult,
      boolean deleted,
      boolean visited,
      boolean markedForDelete) {

    boolean isConditionWithTypeMet(Condition.Type conditionType) {
      return getResultForConditionWithType(conditionType)
          .map(DetailedCondition.Result::isSuccess)
          .orElse(true);
    }

    Optional<DetailedCondition.Result<?>> getResultForConditionWithType(
        Condition.Type conditionType) {
      return switch (conditionType) {
        case ACTIVATION -> Optional.ofNullable(activationConditionResult);
        case DELETE -> Optional.ofNullable(deletePostconditionResult);
        case READY -> Optional.ofNullable(readyPostconditionResult);
        case RECONCILE -> Optional.ofNullable(reconcilePostconditionResult);
      };
    }
  }
}
