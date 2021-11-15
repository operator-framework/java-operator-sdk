package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class DefaultResourceConfiguration<R extends HasMetadata, T extends ResourceConfiguration<R, T>>
    implements ResourceConfiguration<R, T> {

  private final String labelSelector;
  private final Class<R> resourceClass;
  private final String resourceName;
  private final Set<String> namespaces;
  private final boolean watchAllNamespaces;
  private ConfigurationService service;

  public DefaultResourceConfiguration(String resourceName, Class<R> resourceClass,
      Set<String> namespaces, String labelSelector, ConfigurationService service) {
    this.resourceName = resourceName;
    this.namespaces =
        namespaces != null ? Collections.unmodifiableSet(namespaces) : Collections.emptySet();
    this.watchAllNamespaces = this.namespaces.isEmpty();
    this.labelSelector = labelSelector;
    this.resourceClass =
        resourceClass == null ? ResourceConfiguration.super.getResourceClass()
            : resourceClass;
    setConfigurationService(service);
  }

  @Override
  public String getResourceTypeName() {
    return resourceName;
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
  public ConfigurationService getConfigurationService() {
    return service;
  }

  @Override
  public void setConfigurationService(ConfigurationService service) {
    if (this.service != null) {
      throw new RuntimeException("A ConfigurationService is already associated with "
          + identifierForException() + ". Cannot change it once set!");
    }
    this.service = service;
  }

  protected String identifierForException() {
    return getClass().getName();
  }

  @Override
  public String getLabelSelector() {
    return labelSelector;
  }

  @Override
  public Class<R> getResourceClass() {
    return resourceClass;
  }
}
