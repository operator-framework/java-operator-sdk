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

  /**
   * Retrieves the {@link DependentResource} associated with the specified name if it exists,
   * {@link Optional#empty()} otherwise.
   * 
   * @param name the name of the {@link DependentResource} to retrieve
   * @return the {@link DependentResource} associated with the specified name if it exists,
   *         {@link Optional#empty()} otherwise
   */
  public Optional<DependentResource> getDependentResourceByName(String name) {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    return results.keySet().stream().filter(dr -> dr.name().equals(name)).findFirst();
  }

  /**
   * Retrieves the optional result of the condition with the specified type for the specified
   * dependent resource.
   *
   * @param <T> the expected result type of the condition
   * @param dependentResourceName the dependent resource for which we want to retrieve a condition
   *        result
   * @param conditionType the condition type which result we're interested in
   * @param expectedResultType the expected result type of the condition
   * @return the dependent condition result if it exists or {@link Optional#empty()} otherwise
   * @throws IllegalArgumentException if a result exists but is not of the expected type
   */
  public <T> Optional<T> getDependentConditionResult(String dependentResourceName,
      Condition.Type conditionType, Class<T> expectedResultType) {
    return getDependentConditionResult(
        getDependentResourceByName(dependentResourceName).orElse(null), conditionType,
        expectedResultType);
  }

  /**
   * Retrieves the optional result of the condition with the specified type for the specified
   * dependent resource.
   *
   * @param <T> the expected result type of the condition
   * @param dependentResource the dependent resource for which we want to retrieve a condition
   *        result
   * @param conditionType the condition type which result we're interested in
   * @param expectedResultType the expected result type of the condition
   * @return the dependent condition result if it exists or {@link Optional#empty()} otherwise
   * @throws IllegalArgumentException if a result exists but is not of the expected type
   */
  public <T> Optional<T> getDependentConditionResult(DependentResource dependentResource,
      Condition.Type conditionType, Class<T> expectedResultType) {
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
      throw new IllegalArgumentException("Condition " +
          "result " + result[0] +
          " for Dependent " + dependentResource.name() + " doesn't match expected type "
          + expectedResultType.getSimpleName(), e);
    }
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
    private DetailedCondition.Result activationConditionResult;
    private DetailedCondition.Result deletePostconditionResult;
    private DetailedCondition.Result readyPostconditionResult;
    private DetailedCondition.Result reconcilePostconditionResult;
    private boolean deleted;
    private boolean visited;
    private boolean markedForDelete;

    Detail<R> build() {
      return new Detail<>(error, reconcileResult, activationConditionResult,
          deletePostconditionResult, readyPostconditionResult, reconcilePostconditionResult,
          deleted, visited, markedForDelete);
    }

    DetailBuilder<R> withResultForCondition(
        ConditionWithType conditionWithType,
        DetailedCondition.Result conditionResult) {
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


  record Detail<R>(Exception error, ReconcileResult<R> reconcileResult,
      DetailedCondition.Result activationConditionResult,
      DetailedCondition.Result deletePostconditionResult,
      DetailedCondition.Result readyPostconditionResult,
      DetailedCondition.Result reconcilePostconditionResult,
      boolean deleted, boolean visited, boolean markedForDelete) {

    boolean isConditionWithTypeMet(Condition.Type conditionType) {
      return getResultForConditionWithType(conditionType).map(DetailedCondition.Result::isSuccess)
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
