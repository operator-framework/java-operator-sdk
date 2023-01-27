package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <R> Optional<R> getSecondaryResource(Class<R> expectedType) {
    return getSecondaryResource(expectedType, (String) null);
  }

  <R> Set<R> getSecondaryResources(Class<R> expectedType);

  @Deprecated(forRemoval = true)
  <R> Optional<R> getSecondaryResource(Class<R> expectedType, String eventSourceName);

  <R> Set<R> getCachedResources(Class<R> expectedType, String id);

  <R> Set<R> getCachedResources(Class<R> expectedType, ResourceID resourceID);

  default <R> Optional<R> getCachedResource(Class<R> expectedType, String id) {
    var resources = getCachedResources(expectedType, id);
    if (resources.size() > 1) {
      throw new IllegalStateException("Multiple resources found for same id");
    } else {
      return resources.isEmpty() ? Optional.empty() : Optional.of(resources.iterator().next());
    }
  }

  default <R> Optional<R> getCachedResource(Class<R> expectedType, ResourceID resourceID) {
    return getCachedResource(expectedType,
        Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null),
            resourceID.getName()));
  }

  <R> Optional<R> getSecondaryResource(Class<R> expectedType,
      ResourceDiscriminator<R, P> discriminator);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();

  EventSourceRetriever<P> eventSourceRetriever();

}
