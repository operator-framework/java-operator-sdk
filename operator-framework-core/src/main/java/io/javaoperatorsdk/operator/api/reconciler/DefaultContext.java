package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.processing.Controller;

public class DefaultContext<P extends HasMetadata> extends MapAttributeHolder implements Context {

  private final RetryInfo retryInfo;
  private final Controller<P> controller;
  private final P primaryResource;
  private final ConfigurationService configurationService;

  public DefaultContext(RetryInfo retryInfo, Controller<P> controller, P primaryResource) {
    this.retryInfo = retryInfo;
    this.controller = controller;
    this.primaryResource = primaryResource;
    this.configurationService = controller.getConfiguration().getConfigurationService();
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }

  @Override
  public <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName) {
    return controller.getEventSourceManager()
        .getResourceEventSourceFor(expectedType, eventSourceName)
        .flatMap(es -> es.getAssociated(primaryResource));
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return configurationService;
  }
}
