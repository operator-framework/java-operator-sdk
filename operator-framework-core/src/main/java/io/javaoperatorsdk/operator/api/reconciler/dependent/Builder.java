package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@FunctionalInterface
public interface Builder<R, P extends HasMetadata> {
  R buildFor(P primary, Context context);
}
