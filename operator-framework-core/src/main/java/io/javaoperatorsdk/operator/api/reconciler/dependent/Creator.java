package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@SuppressWarnings("rawtypes")
@FunctionalInterface
public interface Creator<R, P extends HasMetadata> {
  Creator NOOP = (desired, primary, context) -> null;

  R create(R desired, P primary, Context context);
}
