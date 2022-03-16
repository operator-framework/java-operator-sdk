package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@FunctionalInterface
public interface Cleaner<P extends HasMetadata> {
  void cleanup(P primary, Context<P> context);
}
