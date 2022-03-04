package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface DependentResource<R, P extends HasMetadata> {
  void reconcile(P primary, Context context);

  default void cleanup(P primary, Context context) {}

  Optional<R> getResource(P primaryResource);
}
