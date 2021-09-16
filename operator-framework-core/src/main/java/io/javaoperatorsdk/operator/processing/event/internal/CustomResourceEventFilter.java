package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

@FunctionalInterface
public interface CustomResourceEventFilter<T extends CustomResource> {

  boolean acceptChange(ControllerConfiguration<T> configuration, T oldResource, T newResource);

  default CustomResourceEventFilter<T> and(CustomResourceEventFilter<T> other) {
    return other == null ? this
        : (ControllerConfiguration<T> configuration, T oldResource, T newResource) -> {
          boolean result = acceptChange(configuration, oldResource, newResource);
          return result && other.acceptChange(configuration, oldResource, newResource);
        };
  }

  default CustomResourceEventFilter<T> or(CustomResourceEventFilter<T> other) {
    return other == null ? this
        : (ControllerConfiguration<T> configuration, T oldResource, T newResource) -> {
          boolean result = acceptChange(configuration, oldResource, newResource);
          return result || other.acceptChange(configuration, oldResource, newResource);
        };
  }
}
