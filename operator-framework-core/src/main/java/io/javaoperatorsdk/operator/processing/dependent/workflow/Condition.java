package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface Condition<R, P extends HasMetadata> {

  enum Type {
    ACTIVATION,
    DELETE,
    READY,
    RECONCILE
  }

  /**
   * Checks whether a condition holds true for a given {@link
   * io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} based on the observed
   * cluster state.
   *
   * @param dependentResource for which the condition applies to
   * @param primary the primary resource being considered
   * @param context the current reconciliation {@link Context}
   * @return {@code true} if the condition holds, {@code false} otherwise
   */
  boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);
}
