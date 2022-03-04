package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@FunctionalInterface
public interface Deleter<P extends HasMetadata> {
  void del(P primary, Context context);
}
