package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.lang.reflect.InvocationTargetException;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

public interface DependentResourceFactory {

  default <T extends DependentResource<?, ?>> T createFrom(DependentResourceSpec<T, ?> spec) {
    try {
      return spec.getDependentResourceClass().getConstructor().newInstance();
    } catch (InstantiationException | NoSuchMethodException | IllegalAccessException
        | InvocationTargetException e) {
      throw new IllegalArgumentException("Cannot instantiate DependentResource "
          + spec.getDependentResourceClass().getCanonicalName(), e);
    }
  }

}
