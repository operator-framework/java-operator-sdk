package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;

@FunctionalInterface
public interface Builder<R extends HasMetadata, P extends HasMetadata> {
  R buildFor(P primary);
}
