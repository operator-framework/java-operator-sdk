package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.lang.reflect.InvocationTargetException;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

public interface DependentResourceFactory {

  default <T extends DependentResource<?, ?>> T createFrom(DependentResourceSpec<T, ?> spec) {
    return createFrom(spec.getDependentResourceClass());
  }

  default <T extends DependentResource<?, ?>> T createFrom(Class<T> dependentResourceClass) {
    try {
      return dependentResourceClass.getConstructor().newInstance();
    } catch (InstantiationException | NoSuchMethodException | IllegalAccessException
        | InvocationTargetException e) {
      throw new IllegalArgumentException("Cannot instantiate DependentResource "
          + dependentResourceClass.getCanonicalName(), e);
    }
  }

}
