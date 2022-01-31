package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface DependentResourceConfiguration<R, P extends HasMetadata> {

  Class<? extends DependentResource<R, P>> getDependentResourceClass();

  Class<R> getResourceClass();
}
