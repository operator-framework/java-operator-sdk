package io.javaoperatorsdk.operator.api.reconciler;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <T> Optional<T> getSecondaryResource(Class<T> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <T> List<T> getSecondaryResources(Class<T> expectedType);

  <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();
}
