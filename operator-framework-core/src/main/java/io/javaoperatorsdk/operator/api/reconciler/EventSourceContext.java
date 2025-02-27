package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

/**
 * Contextual information made available to event sources.
 *
 * @param <P> the type associated with the primary resource that is handled by your reconciler
 */
public class EventSourceContext<P extends HasMetadata> {

  private final IndexerResourceCache<P> primaryCache;
  private final ControllerConfiguration<P> controllerConfiguration;
  private final KubernetesClient client;
  private final Class<P> primaryResourceClass;

  public EventSourceContext(
      IndexerResourceCache<P> primaryCache,
      ControllerConfiguration<P> controllerConfiguration,
      KubernetesClient client,
      Class<P> primaryResourceClass) {
    this.primaryCache = primaryCache;
    this.controllerConfiguration = controllerConfiguration;
    this.client = client;
    this.primaryResourceClass = primaryResourceClass;
  }

  /**
   * Retrieves the cache that an {@link EventSource} can query to retrieve primary resources
   *
   * @return the primary resource cache
   */
  public IndexerResourceCache<P> getPrimaryCache() {
    return primaryCache;
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
   * Provides access to the {@link KubernetesClient} used by the current {@link
   * io.javaoperatorsdk.operator.Operator} instance.
   *
   * @return the {@link KubernetesClient} used by the current {@link
   *     io.javaoperatorsdk.operator.Operator} instance.
   */
  public KubernetesClient getClient() {
    return client;
  }

  public Class<P> getPrimaryResourceClass() {
    return primaryResourceClass;
  }
}
