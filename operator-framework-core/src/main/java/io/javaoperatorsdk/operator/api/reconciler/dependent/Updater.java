package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@FunctionalInterface
public interface Updater<R, P extends HasMetadata> {

  R update(R fetched, P primary, Context context);
}
