package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.List;
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
  private final List<DependentResource> dependents;
  private ConfigurationService service;

  // NOSONAR constructor is meant to provide all information
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
      ConfigurationService service,
      List<DependentResource> dependents) {
    this.associatedControllerClassName = associatedControllerClassName;
    this.name = name;
    this.crdName = crdName;
    this.finalizer = finalizer;
    this.generationAware = generationAware;
    this.namespaces =
        namespaces != null ? Collections.unmodifiableSet(namespaces) : Collections.emptySet();
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
    this.dependents = dependents != null ? dependents : Collections.emptyList();
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
      throw new IllegalStateException("A ConfigurationService is already associated with '" + name
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
  public List<DependentResource> getDependentResources() {
    return dependents;
  }
}
