package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Condition<R, P extends HasMetadata> {

  /**
   * Checks whether a condition holds true for a given
   * {@link io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} based on the
   * observed cluster state.
   *
   * @param primary the primary resource being considered
   * @param secondary the secondary resource associated with the specified primary resource or
   *        {@code null} if no such secondary resource exists (for example because it's been
   *        deleted)
   * @param context the current reconciliation {@link Context}
   * @return {@code true} if the condition holds, {@code false} otherwise
   */
  boolean isMet(P primary, R secondary, Context<P> context);
}
