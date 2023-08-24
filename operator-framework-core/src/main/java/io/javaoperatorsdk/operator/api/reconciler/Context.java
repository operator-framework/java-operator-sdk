package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <R> Optional<R> getSecondaryResource(Class<R> resourceType) {
    return getSecondaryResource(resourceType, (String) null);
  }

  <R> Set<R> getSecondaryResources(Class<R> resourceType);

  default <R> Stream<R> getSecondaryResourcesAsStream(Class<R> resourceType) {
    return getSecondaryResources(resourceType).stream();
  }

  @Deprecated(forRemoval = true)
  <R> Optional<R> getSecondaryResource(Class<R> resourceType, String eventSourceName);

  <R> Optional<R> getSecondaryResource(Class<R> resourceType,
      ResourceDiscriminator<R, P> discriminator);

  <R extends HasMetadata> Optional<R> getResource(Class<R> resourceType, String name,
      String namespace);


  <R extends HasMetadata> Optional<R> getResource(Class<R> resourceType, String eventSourceName,
      String name, String namespace);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();

  EventSourceRetriever<P> eventSourceRetriever();

  KubernetesClient getClient();

  /**
   * ExecutorService initialized by framework for workflows. Used for workflow standalone mode.
   */
  ExecutorService getWorkflowExecutorService();
}
