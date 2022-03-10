package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface DependentResource<R, P extends HasMetadata> {
  Optional<R> reconcile(P primary, Context<P> context);

  default void cleanup(P primary, Context<P> context) {}

  Optional<R> getResource(P primaryResource);
}
