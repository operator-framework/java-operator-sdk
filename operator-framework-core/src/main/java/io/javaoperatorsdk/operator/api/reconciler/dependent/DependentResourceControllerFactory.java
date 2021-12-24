package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.DependentResource;

public interface DependentResourceControllerFactory<P extends HasMetadata> {

  default <R> DependentResourceController<R, P> from(DependentResource<R, P> dependent) {
    // todo: this needs to be cleaned-up / redesigned
    return dependent instanceof DependentResourceController
        ? (DependentResourceController<R, P>) dependent
        : new DependentResourceController<>(dependent);
  }
}
