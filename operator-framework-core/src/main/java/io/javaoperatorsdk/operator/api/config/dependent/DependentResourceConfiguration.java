package io.javaoperatorsdk.operator.api.config.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface DependentResourceConfiguration<R, P extends HasMetadata> {

  Class<? extends DependentResource<R, P>> getDependentResourceClass();

  Class<R> getResourceClass();
}
