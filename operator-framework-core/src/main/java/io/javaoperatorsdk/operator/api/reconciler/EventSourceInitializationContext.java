package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

/**
 * Contextual information made available to prepare event sources.
 *
 * @param <P> the type associated with the primary resource that is handled by your reconciler
 */
public class EventSourceInitializationContext<P extends HasMetadata> {

  private final ResourceCache<P> primaryCache;
  private final ConfigurationService configurationService;

  public EventSourceInitializationContext(ResourceCache<P> primaryCache,
      ConfigurationService configurationService) {
    this.primaryCache = primaryCache;
    this.configurationService = configurationService;
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
}
