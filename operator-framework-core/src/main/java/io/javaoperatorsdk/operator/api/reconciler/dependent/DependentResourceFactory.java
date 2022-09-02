package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

@Deprecated
@SuppressWarnings({"rawtypes", "unchecked"})
public interface DependentResourceFactory {

  default DependentResource createFrom(DependentResourceSpec spec) {
    return createFrom(spec.getDependentResourceClass());
  }

  default <T extends DependentResource> T createFrom(Class<T> dependentResourceClass) {
    return (T) Utils.instantiate(dependentResourceClass, DependentResource.class, null);
  }

}
