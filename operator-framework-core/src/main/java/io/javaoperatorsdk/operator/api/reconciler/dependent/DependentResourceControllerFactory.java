package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.lang.reflect.InvocationTargetException;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.DependentResourceConfiguration;

public interface DependentResourceControllerFactory<P extends HasMetadata> {

  default <T extends DependentResourceController<R, P, C>, R, C extends DependentResourceConfiguration<R, P>> T from(
      C config) {
    try {
      final var dependentResource = config.getDependentResourceClass().getConstructor()
          .newInstance();
      if (config instanceof KubernetesDependentResourceConfiguration) {
        return (T) new KubernetesDependentResourceController(dependentResource,
            (KubernetesDependentResourceConfiguration) config);
      } else {
        return (T) new DependentResourceController(dependentResource, config);
      }
    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
        | IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
