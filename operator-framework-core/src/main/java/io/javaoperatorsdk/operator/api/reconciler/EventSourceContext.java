package io.javaoperatorsdk.operator.api.reconciler;

import java.util.concurrent.CompletionStage;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

/**
 * Contextual information made available to event sources.
 *
 * @param <P> the type associated with the primary resource that is handled by your reconciler
 */
public class EventSourceContext<P extends HasMetadata> {

  private final ControllerConfiguration<P> controllerConfiguration;
  private final KubernetesClient client;
  private final EventSourceManager<P> eventSourceManager;

  public EventSourceContext(EventSourceManager<P> eventSourceManager,
      ControllerConfiguration<P> controllerConfiguration,
      KubernetesClient client) {
    this.eventSourceManager = eventSourceManager;
    this.controllerConfiguration = controllerConfiguration;
    this.client = client;
  }

  /**
   * Retrieves the cache that an {@link EventSource} can query to retrieve primary resources
   *
   * @return the primary resource cache
   */
  public IndexerResourceCache<P> getPrimaryCache() {
    return eventSourceManager.getControllerResourceEventSource();
  }

  /**
   * Retrieves the {@link ControllerConfiguration} associated with the operator. This allows, in
   * particular, to lookup controller and global configuration information such as the configured*
   *
   * @return the {@link ControllerConfiguration} associated with the operator
   */
  public ControllerConfiguration<P> getControllerConfiguration() {
    return controllerConfiguration;
  }

  /**
   * Provides access to the {@link KubernetesClient} used by the current
   * {@link io.javaoperatorsdk.operator.Operator} instance.
   *
   * @return the {@link KubernetesClient} used by the current
   *         {@link io.javaoperatorsdk.operator.Operator} instance.
   */
  public KubernetesClient getClient() {
    return client;
  }

  public <R> CompletionStage<ResourceEventSource<R, P>> getResourceEventSourceWhenStartedFor(
      Class<R> dependentType, String name) {
    return eventSourceManager.getResourceEventSourceWhenStartedFor(dependentType, name);
  }
}
