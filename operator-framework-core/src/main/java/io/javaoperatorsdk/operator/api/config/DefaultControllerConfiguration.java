package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

public class DefaultControllerConfiguration<R extends HasMetadata>
    implements ControllerConfiguration<R> {

  private final String associatedControllerClassName;
  private final String name;
  private final String crdName;
  private final String finalizer;
  private final boolean generationAware;
  private final Set<String> namespaces;
  private final boolean watchAllNamespaces;
  private final RetryConfiguration retryConfiguration;
  private final String labelSelector;
  private final ResourceEventFilter<R> resourceEventFilter;
  private final Class<R> resourceClass;
  private final Duration reconciliationMaxInterval;
  private ConfigurationService service;

  public DefaultControllerConfiguration(
      String associatedControllerClassName,
      String name,
      String crdName,
      String finalizer,
      boolean generationAware,
      Set<String> namespaces,
      RetryConfiguration retryConfiguration,
      String labelSelector,
      ResourceEventFilter<R> resourceEventFilter,
      Class<R> resourceClass,
      Duration reconciliationMaxInterval,
      ConfigurationService service) {
    this.associatedControllerClassName = associatedControllerClassName;
    this.name = name;
    this.crdName = crdName;
    this.finalizer = finalizer;
    this.generationAware = generationAware;
    this.namespaces =
        namespaces != null ? Collections.unmodifiableSet(namespaces) : Collections.emptySet();
    this.reconciliationMaxInterval = reconciliationMaxInterval;
    this.watchAllNamespaces = this.namespaces.isEmpty();
    this.retryConfiguration =
        retryConfiguration == null
            ? ControllerConfiguration.super.getRetryConfiguration()
            : retryConfiguration;
    this.labelSelector = labelSelector;
    this.resourceEventFilter = resourceEventFilter;
    this.resourceClass =
        resourceClass == null ? ControllerConfiguration.super.getResourceClass()
            : resourceClass;
    setConfigurationService(service);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getResourceTypeName() {
    return crdName;
  }

  @Override
  public String getFinalizer() {
    return finalizer;
  }

  @Override
  public boolean isGenerationAware() {
    return generationAware;
  }

  @Override
  public String getAssociatedReconcilerClassName() {
    return associatedControllerClassName;
  }

  @Override
  public Set<String> getNamespaces() {
    return namespaces;
  }

  @Override
  public boolean watchAllNamespaces() {
    return watchAllNamespaces;
  }

  @Override
  public RetryConfiguration getRetryConfiguration() {
    return retryConfiguration;
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return service;
  }

  @Override
  public void setConfigurationService(ConfigurationService service) {
    if (this.service != null) {
      throw new RuntimeException("A ConfigurationService is already associated with '" + name
          + "' ControllerConfiguration. Cannot change it once set!");
    }
    this.service = service;
  }

  @Override
  public String getLabelSelector() {
    return labelSelector;
  }

  @Override
  public Class<R> getResourceClass() {
    return resourceClass;
  }

  @Override
  public ResourceEventFilter<R> getEventFilter() {
    return resourceEventFilter;
  }

  @Override
  public Optional<Duration> reconciliationMaxInterval() {
    return Optional.ofNullable(reconciliationMaxInterval);
  }
}
