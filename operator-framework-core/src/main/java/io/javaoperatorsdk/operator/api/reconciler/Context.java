package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ManagedDependentResourceContext;

public interface Context {

  Optional<RetryInfo> getRetryInfo();

  default <T> Optional<T> getSecondaryResource(Class<T> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName);

  ConfigurationService getConfigurationService();

  ManagedDependentResourceContext managedDependentResourceContext();
}
