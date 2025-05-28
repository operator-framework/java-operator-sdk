package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public interface WorkflowResult {
  default Map<DependentResource, Exception> getErroredDependents() {
    return Map.of();
  }

  /**
   * Retrieves the {@link DependentResource} associated with the specified name if it exists, {@link
   * Optional#empty()} otherwise.
   *
   * @param name the name of the {@link DependentResource} to retrieve
   * @return the {@link DependentResource} associated with the specified name if it exists, {@link
   *     Optional#empty()} otherwise
   */
  default Optional<DependentResource> getDependentResourceByName(String name) {
    return Optional.empty();
  }

  /**
   * Retrieves the optional result of the condition with the specified type for the specified
   * dependent resource.
   *
   * @param <T> the expected result type of the condition
   * @param dependentResourceName the dependent resource for which we want to retrieve a condition
   *     result
   * @param conditionType the condition type which result we're interested in
   * @param expectedResultType the expected result type of the condition
   * @return the dependent condition result if it exists or {@link Optional#empty()} otherwise
   * @throws IllegalArgumentException if a result exists but is not of the expected type
   */
  default <T> Optional<T> getDependentConditionResult(
      String dependentResourceName, Condition.Type conditionType, Class<T> expectedResultType) {
    return getDependentConditionResult(
        getDependentResourceByName(dependentResourceName).orElse(null),
        conditionType,
        expectedResultType);
  }

  /**
   * Retrieves the optional result of the condition with the specified type for the specified
   * dependent resource.
   *
   * @param <T> the expected result type of the condition
   * @param dependentResource the dependent resource for which we want to retrieve a condition
   *     result
   * @param conditionType the condition type which result we're interested in
   * @param expectedResultType the expected result type of the condition
   * @return the dependent condition result if it exists or {@link Optional#empty()} otherwise
   * @throws IllegalArgumentException if a result exists but is not of the expected type
   */
  default <T> Optional<T> getDependentConditionResult(
      DependentResource dependentResource,
      Condition.Type conditionType,
      Class<T> expectedResultType) {
    return Optional.empty();
  }

  default boolean erroredDependentsExist() {
    return false;
  }

  default void throwAggregateExceptionIfErrorsPresent() {
    throw new UnsupportedOperationException("Implement this method");
  }
}
