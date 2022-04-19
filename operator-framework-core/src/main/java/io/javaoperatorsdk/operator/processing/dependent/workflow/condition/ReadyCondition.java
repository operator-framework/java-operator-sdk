package io.javaoperatorsdk.operator.processing.dependent.workflow.condition;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface ReadyCondition<R, P extends HasMetadata> {

  boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);

  /**
   * If condition not met, the primary resource status might be updated by overriding this method.
   * In case there are multiple conditions in a workflow it is updated multiple times.
   *
   * @param primary
   */
  default void addNotReadyStatusInfo(P primary) {}
}
