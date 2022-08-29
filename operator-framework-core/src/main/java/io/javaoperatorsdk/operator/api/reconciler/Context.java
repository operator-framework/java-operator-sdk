package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <R> Optional<R> getSecondaryResource(Class<R> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <R> Set<R> getSecondaryResources(Class<R> expectedType);

  @Deprecated(forRemoval = true)
  <R> Optional<R> getSecondaryResource(Class<R> expectedType, String eventSourceName);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();
}
