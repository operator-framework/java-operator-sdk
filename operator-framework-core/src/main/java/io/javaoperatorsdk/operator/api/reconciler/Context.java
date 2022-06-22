package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <T> Optional<T> getSecondaryResource(Class<T> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <T> Set<T> getSecondaryResources(Class<T> expectedType);

  <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();

  // todo access event sources from context
}
