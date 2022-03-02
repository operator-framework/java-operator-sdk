package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@SuppressWarnings("rawtypes")
@FunctionalInterface
public interface Deleter<P extends HasMetadata> {
  Deleter NOOP = (primary, context) -> {
  };

  void delete(P primary, Context context);
}
