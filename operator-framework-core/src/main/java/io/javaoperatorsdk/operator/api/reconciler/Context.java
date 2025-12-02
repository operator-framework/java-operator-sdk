/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <R> Optional<R> getSecondaryResource(Class<R> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <R> Set<R> getSecondaryResources(Class<R> expectedType);

  default <R> Stream<R> getSecondaryResourcesAsStream(Class<R> expectedType) {
    return getSecondaryResources(expectedType).stream();
  }

  <R> Optional<R> getSecondaryResource(Class<R> expectedType, String eventSourceName);

  ControllerConfiguration<P> getControllerConfiguration();

  /**
   * Retrieve the {@link ManagedWorkflowAndDependentResourceContext} used to interact with {@link
   * io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource}s and associated {@link
   * io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow}
   *
   * @return the {@link ManagedWorkflowAndDependentResourceContext}
   */
  ManagedWorkflowAndDependentResourceContext managedWorkflowAndDependentResourceContext();

  EventSourceRetriever<P> eventSourceRetriever();

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

  /**
   * To check if the primary resource is already deleted. This value can be true only if you turn on
   * {@link
   * io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration#triggerReconcilerOnAllEvents()}
   *
   * @return true Delete event received for primary resource
   * @since 5.2.0
   */
  boolean isPrimaryResourceDeleted();

  /**
   * Check this only if {@link #isPrimaryResourceDeleted()} is true.
   *
   * @return true if the primary resource is deleted, but the last known state is only available
   *     from the caches of the underlying Informer, not from Delete event.
   * @since 5.2.0
   */
  boolean isPrimaryResourceFinalStateUnknown();
}
