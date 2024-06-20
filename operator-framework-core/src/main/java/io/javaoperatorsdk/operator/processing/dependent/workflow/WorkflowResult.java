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
class WorkflowResult {
  private final Map<DependentResource, Detail<?>> results;
  private Boolean hasErroredDependents;

  WorkflowResult(Map<DependentResource, Detail<?>> results) {
    this.results = results;
  }

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

  protected List<DependentResource> listFilteredBy(
      Function<Detail, Boolean> filter) {
    return results.entrySet().stream()
        .filter(e -> filter.apply(e.getValue()))
        .map(Map.Entry::getKey)
        .toList();
  }

  public boolean erroredDependentsExist() {
    if (hasErroredDependents == null) {
      hasErroredDependents = !getErroredDependents().isEmpty();
    }
    return hasErroredDependents;
  }

  public void throwAggregateExceptionIfErrorsPresent() {
    if (erroredDependentsExist()) {
      throw new AggregatedOperatorException("Exception(s) during workflow execution.",
          getErroredDependentsStream()
              .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().error)));
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  static class DetailBuilder<R> {
    private Exception error;
    private ReconcileResult<R> reconcileResult;
    private ResultCondition.Result activationConditionResult;
    private ResultCondition.Result deletePostconditionResult;
    private ResultCondition.Result readyPostconditionResult;
    private ResultCondition.Result reconcilePostconditionResult;
    private boolean deleted;
    private boolean visited;

    Detail<R> build() {
      return new Detail<>(error, reconcileResult, activationConditionResult,
          deletePostconditionResult, readyPostconditionResult, reconcilePostconditionResult, deleted, visited);
    }

    DetailBuilder<R> withResultForCondition(DependentResourceNode.ConditionWithType conditionWithType, ResultCondition.Result conditionResult) {
      switch (conditionWithType.type()) {
        case ACTIVATION -> activationConditionResult = conditionResult;
        case DELETE -> deletePostconditionResult = conditionResult;
        case READY -> readyPostconditionResult = conditionResult;
        case RECONCILE -> reconcilePostconditionResult = conditionResult;
        default -> throw new IllegalStateException("Unexpected condition type: " + conditionWithType);
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

    DetailBuilder<R> withDeleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public boolean hasError() {
      return error != null;
    }

    public boolean hasPostDeleteConditionNotMet() {
      return deletePostconditionResult != null && !deletePostconditionResult.isSuccess();
    }

    public boolean isNotReady() {
      return readyPostconditionResult != null && !readyPostconditionResult.isSuccess();
    }

    DetailBuilder<R> markAsVisited() {
      visited = true;
      return this;
    }

    public boolean isVisited() {
      return visited;
    }
  }


  record Detail<R>(Exception error, ReconcileResult<R> reconcileResult,
      ResultCondition.Result activationConditionResult,
      ResultCondition.Result deletePostconditionResult,
      ResultCondition.Result readyPostconditionResult,
      ResultCondition.Result reconcilePostconditionResult,
                   boolean deleted, boolean visited) {

    boolean isConditionWithTypeMet(Condition.Type conditionType) {
      return getResultForConditionWithType(conditionType).map(ResultCondition.Result::isSuccess).orElse(true);
    }

    Optional<ResultCondition.Result<?>> getResultForConditionWithType(Condition.Type conditionType) {
      return switch (conditionType) {
        case ACTIVATION -> Optional.ofNullable(activationConditionResult);
        case DELETE -> Optional.ofNullable(deletePostconditionResult);
        case READY -> Optional.ofNullable(readyPostconditionResult);
        case RECONCILE -> Optional.ofNullable(reconcilePostconditionResult);
      };
    }
  }
}
