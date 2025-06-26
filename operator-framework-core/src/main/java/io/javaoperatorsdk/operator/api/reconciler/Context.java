package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

public interface Context<P extends HasMetadata> extends CacheAware<P> {

  Optional<RetryInfo> getRetryInfo();

  /**
   * Retrieve the {@link ManagedWorkflowAndDependentResourceContext} used to interact with {@link
   * io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource}s and associated {@link
   * io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow}
   *
   * @return the {@link ManagedWorkflowAndDependentResourceContext}
   */
  ManagedWorkflowAndDependentResourceContext managedWorkflowAndDependentResourceContext();

  KubernetesClient getClient();

  /** ExecutorService initialized by framework for workflows. Used for workflow standalone mode. */
  ExecutorService getWorkflowExecutorService();

  /**
   * Retrieves the primary resource.
   *
   * @return the primary resource associated with the current reconciliation
   */
  P getPrimaryResource();

  /**
   * Retrieves the primary resource cache.
   *
   * @return the {@link IndexerResourceCache} associated with the associated {@link Reconciler} for
   *     this context
   */
  @SuppressWarnings("unused")
  IndexedResourceCache<P> getPrimaryCache();

  /**
   * Determines whether a new reconciliation will be triggered right after the current
   * reconciliation is finished. This allows to optimize certain situations, helping avoid unneeded
   * API calls. A reconciler might, for example, skip updating the status when it's known another
   * reconciliation is already scheduled, which would in turn trigger another status update, thus
   * rendering the current one moot.
   *
   * @return {@code true} is another reconciliation is already scheduled, {@code false} otherwise
   */
  boolean isNextReconciliationImminent();
}
