package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface ReadOnlyDependentResource<R, P extends HasMetadata>
    extends DependentResource<R, P> {

  @Override
  default void reconcile(HasMetadata primary, Context context) {}

  @Override
  default void delete(HasMetadata primary, Context context) {}

}
