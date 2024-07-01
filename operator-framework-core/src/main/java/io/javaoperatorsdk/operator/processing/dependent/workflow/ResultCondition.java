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

  interface Result<T> {
    Result metWithoutResult = new Result() {
      @Override
      public Object getResult() {
        return null;
      }

      @Override
      public boolean isSuccess() {
        return true;
      }

      @Override
      public String toString() {
        return asString();
      }
    };

    Result unmetWithoutResult = new Result() {
      @Override
      public Object getResult() {
        return null;
      }

      @Override
      public boolean isSuccess() {
        return false;
      }

      @Override
      public String toString() {
        return asString();
      }
    };

    static Result withoutResult(boolean success) {
      return success ? metWithoutResult : unmetWithoutResult;
    }

    default String asString() {
      return "Result: " + getResult() + " met: " + isSuccess();
    }

    T getResult();

    boolean isSuccess();
  }
}
