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
    T getResult();

    boolean isSuccess();
  }
}
