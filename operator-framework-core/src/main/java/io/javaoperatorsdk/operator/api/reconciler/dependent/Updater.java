package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@SuppressWarnings("rawtypes")
@FunctionalInterface
public interface Updater<R, P extends HasMetadata> {
  Updater NOOP = (actual, desired, primary, context) -> null;

  R update(R actual, R desired, P primary, Context context);

  default boolean match(R actualResource, R desiredResource, Context context) {
    return Objects.equals(actualResource, desiredResource);
  }
}
