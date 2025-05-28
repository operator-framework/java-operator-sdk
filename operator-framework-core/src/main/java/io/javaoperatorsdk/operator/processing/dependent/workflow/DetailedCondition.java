package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

/**
 * A condition that can return extra information in addition of whether it is met or not.
 *
 * @param <R> the resource type this condition applies to
 * @param <P> the primary resource type associated with the dependent workflow this condition is
 *     part of
 * @param <T> the type of the extra information returned by the condition
 */
public interface DetailedCondition<R, P extends HasMetadata, T> extends Condition<R, P> {

  /**
   * Checks whether a condition holds true for the specified {@link DependentResource}, returning
   * additional information as needed.
   *
   * @param dependentResource the {@link DependentResource} for which we want to check the condition
   * @param primary the primary resource being considered
   * @param context the current reconciliation {@link Context}
   * @return a {@link Result} instance containing the result of the evaluation of the condition as
   *     well as additional detail
   * @see Condition#isMet(DependentResource, HasMetadata, Context)
   */
  Result<T> detailedIsMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);

  @Override
  default boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context) {
    return detailedIsMet(dependentResource, primary, context).isSuccess();
  }

  /**
   * Holds a more detailed {@link Condition} result.
   *
   * @param <T> the type of the extra information provided in condition evaluation
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  interface Result<T> {
    /** A result expressing a condition has been met without extra information */
    Result metWithoutResult = new DefaultResult(true, null);

    /** A result expressing a condition has not been met without extra information */
    Result unmetWithoutResult = new DefaultResult(false, null);

    /**
     * Creates a {@link Result} without extra information
     *
     * @param success whether or not the condition has been met
     * @return a {@link Result} without extra information
     */
    static Result withoutResult(boolean success) {
      return success ? metWithoutResult : unmetWithoutResult;
    }

    /**
     * Creates a {@link Result} with the specified condition evaluation result and extra information
     *
     * @param success whether or not the condition has been met
     * @param detail the extra information that the condition provided during its evaluation
     * @return a {@link Result} with the specified condition evaluation result and extra information
     * @param <T> the type of the extra information provided by the condition
     */
    static <T> Result<T> withResult(boolean success, T detail) {
      return new DefaultResult<>(success, detail);
    }

    default String asString() {
      return "Detail: " + getDetail() + " met: " + isSuccess();
    }

    /**
     * The extra information provided by the associated {@link DetailedCondition} during its
     * evaluation
     *
     * @return extra information provided by the associated {@link DetailedCondition} during its
     *     evaluation or {@code null} if none was provided
     */
    T getDetail();

    /**
     * Whether the associated condition held true
     *
     * @return {@code true} if the associated condition was met, {@code false} otherwise
     */
    boolean isSuccess();
  }
}
