package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ResourceEventAware {

  default void onResourceCreated(HasMetadata resource) {}

  default void onResourceUpdated(HasMetadata newResource, HasMetadata oldResource) {}

  default void onResourceDeleted(HasMetadata resource) {}

}
