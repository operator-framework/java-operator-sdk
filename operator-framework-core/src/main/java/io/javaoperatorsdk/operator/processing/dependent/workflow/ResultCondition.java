package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface ResultCondition<R, P extends HasMetadata, T> extends Condition<R, P> {
  Result<T> detailedIsMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);

  Object NULL = new Object();

  @Override
  default boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context) {
    return detailedIsMet(dependentResource, primary, context).isSuccess();
  }

  interface Result<T> {
    Result metWithoutResult = new Result() {
      @Override
      public Object getResult() {
        return NULL;
      }

      @Override
      public boolean isSuccess() {
        return true;
      }
    };

    Result unmetWithoutResult = new Result() {
      @Override
      public Object getResult() {
        return NULL;
      }

      @Override
      public boolean isSuccess() {
        return false;
      }
    };

    static Result withoutResult(boolean success) {
      return success ? metWithoutResult : unmetWithoutResult;
    }

    T getResult();

    boolean isSuccess();
  }
}
