package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;

@FunctionalInterface
public interface Updater<R extends HasMetadata, P extends HasMetadata> {

  R update(R fetched, P primary);
}
