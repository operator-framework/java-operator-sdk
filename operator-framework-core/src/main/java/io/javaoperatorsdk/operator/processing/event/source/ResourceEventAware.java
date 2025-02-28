package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ResourceEventAware<T extends HasMetadata> {

  default void onResourceCreated(T resource) {}

  default void onResourceUpdated(T newResource, T oldResource) {}

  default void onResourceDeleted(T resource) {}
}
