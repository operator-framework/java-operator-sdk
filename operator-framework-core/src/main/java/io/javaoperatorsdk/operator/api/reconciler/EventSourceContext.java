package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

/**
 * Contextual information made available to event sources.
 *
 * @param <P> the type associated with the primary resource that is handled by your reconciler
 */
public class EventSourceContext<P extends HasMetadata> extends MapAttributeHolder {

  private final ResourceCache<P> primaryCache;
  private final ConfigurationService configurationService;
  private final KubernetesClient client;

  public EventSourceContext(ResourceCache<P> primaryCache,
      ConfigurationService configurationService, KubernetesClient client) {
    this.primaryCache = primaryCache;
    this.configurationService = configurationService;
    this.client = client;
  }

  /**
   * Retrieves the cache that an {@link EventSource} can query to retrieve primary resources
   *
   * @return the primary resource cache
   */
  public ResourceCache<P> getPrimaryCache() {
    return primaryCache;
  }

  /**
   * Retrieves the {@link ConfigurationService} associated with the operator. This allows, in
   * particular, to lookup global configuration information such as the configured
   * {@link io.javaoperatorsdk.operator.api.monitoring.Metrics} or
   * {@link io.javaoperatorsdk.operator.api.config.Cloner} implementations, which could be useful to
   * event sources.
   * 
   * @return the {@link ConfigurationService} associated with the operator
   */
  public ConfigurationService getConfigurationService() {
    return configurationService;
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
}
