package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final String labelSelector;
  private final Set<String> namespaces;
  private final boolean watchAllNamespaces;
  private final Class<R> resourceClass;
  private ConfigurationService service;

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      String... namespaces) {
    this(labelSelector, resourceClass,
        namespaces != null ? Set.of(namespaces) : Collections.emptySet());
  }

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      Set<String> namespaces) {
    this.labelSelector = labelSelector;
    this.resourceClass = resourceClass;
    this.namespaces = namespaces != null ? namespaces : Collections.emptySet();
    this.watchAllNamespaces = this.namespaces.isEmpty();
  }

  @Override
  public String getResourceTypeName() {
    return ResourceConfiguration.super.getResourceTypeName();
  }

  @Override
  public String getLabelSelector() {
    return labelSelector;
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
  public Class<R> getResourceClass() {
    return resourceClass;
  }

  @Override
  public void setConfigurationService(ConfigurationService service) {
    this.service = service;
  }
}
