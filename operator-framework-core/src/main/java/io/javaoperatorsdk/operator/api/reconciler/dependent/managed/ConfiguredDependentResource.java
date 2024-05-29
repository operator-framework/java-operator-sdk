package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;

public interface ConfiguredDependentResource<C> {
  void configureWith(C config);

  Optional<C> configuration();
}
