package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@FunctionalInterface
public interface Creator<R, P extends HasMetadata> {
  R create(R desired, P primary, Context context);
}
