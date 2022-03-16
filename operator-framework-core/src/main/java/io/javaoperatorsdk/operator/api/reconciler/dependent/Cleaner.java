package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * DependentResource can implement this interface to denote it requires explicit logic to clean up
 * resources.
 *
 * @param <P> primary resource type
 */
@FunctionalInterface
public interface Cleaner<P extends HasMetadata> {
  void cleanup(P primary, Context<P> context);
}
