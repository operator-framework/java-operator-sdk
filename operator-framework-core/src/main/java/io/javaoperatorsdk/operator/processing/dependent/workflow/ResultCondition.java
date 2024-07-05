package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface ResultCondition<R, P extends HasMetadata, T> extends Condition<R, P> {
  Result<T> detailedIsMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);

  @Override
  default boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context) {
    return detailedIsMet(dependentResource, primary, context).isSuccess();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  interface Result<T> {
    ResultCondition.Result metWithoutResult = new DefaultResult(true, null);

    ResultCondition.Result unmetWithoutResult = new DefaultResult(false, null);

    static Result withoutResult(boolean success) {
      return success ? metWithoutResult : unmetWithoutResult;
    }

    static <T> Result<T> withResult(boolean success, T result) {
      return new DefaultResult<>(success, result);
    }

    default String asString() {
      return "Result: " + getResult() + " met: " + isSuccess();
    }

    T getResult();

    boolean isSuccess();
  }
}
