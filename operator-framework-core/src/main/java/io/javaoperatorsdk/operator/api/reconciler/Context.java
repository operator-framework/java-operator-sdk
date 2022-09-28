package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <R> Optional<R> getSecondaryResource(Class<R> expectedType) {
    return getSecondaryResource(expectedType, (String) null);
  }

  <R> Set<R> getSecondaryResources(Class<R> expectedType);

  @Deprecated(forRemoval = true)
  <R> Optional<R> getSecondaryResource(Class<R> expectedType, String eventSourceName);

  <R> Optional<R> getSecondaryResource(Class<R> expectedType,
      ResourceDiscriminator<R, P> discriminator);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();

  EventSourceRetriever<P> eventSourceRetriever();
}
