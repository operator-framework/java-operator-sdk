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
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <R> Optional<R> getSecondaryResource(Class<R> expectedType) {
    return getSecondaryResource(expectedType, (String) null);
  }

  <R> Set<R> getSecondaryResources(Class<R> expectedType);

  default <R> Stream<R> getSecondaryResourcesAsStream(Class<R> expectedType) {
    return getSecondaryResources(expectedType).stream();
  }

  @Deprecated(forRemoval = true)
  <R> Optional<R> getSecondaryResource(Class<R> expectedType, String eventSourceName);

  <R> Optional<R> getSecondaryResource(Class<R> expectedType,
      ResourceDiscriminator<R, P> discriminator);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();

  EventSourceRetriever<P> eventSourceRetriever();

  KubernetesClient getClient();

  /**
   * ExecutorService initialized by framework for workflows. Used for workflow standalone mode.
   */
  ExecutorService getWorkflowExecutorService();

  /**
   * Retrieves the primary resource cache.
   *
   * @return the {@link IndexerResourceCache} associated with the associated {@link Reconciler} for
   *         this context
   */
  IndexedResourceCache<P> getPrimaryCache();

  /**
   * Returns true if a new reconciliation will be triggered rights after the current reconciliation is finished.
   * This allows to optimize certain situations, for example like reconciler might not want to even do a status update if
   * there is already a next reconciliation scheduled since that would do an additional status update, thus an additional
   * API call (what is ideally reduced to minimal).
   **/
  boolean isNextReconciliationImminent();

}
