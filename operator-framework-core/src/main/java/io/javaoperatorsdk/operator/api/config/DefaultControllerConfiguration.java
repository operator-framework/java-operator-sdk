package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;

public class DefaultControllerConfiguration<R extends CustomResource<?, ?>>
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
  private Class<R> customResourceClass;
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
      Class<R> customResourceClass,
      ConfigurationService service) {
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
    this.customResourceClass =
        customResourceClass == null ? ControllerConfiguration.super.getCustomResourceClass()
            : customResourceClass;
    setConfigurationService(service);
  }

  /**
   * @deprecated use
   *             {@link #DefaultControllerConfiguration(String, String, String, String, boolean, Set, RetryConfiguration, String, Class, ConfigurationService)}
   *             instead
   */
  @Deprecated
  public DefaultControllerConfiguration(
      String associatedControllerClassName,
      String name,
      String crdName,
      String finalizer,
      boolean generationAware,
      Set<String> namespaces,
      RetryConfiguration retryConfiguration) {
    this(associatedControllerClassName, name, crdName, finalizer, generationAware, namespaces,
        retryConfiguration, null, null, null);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getCRDName() {
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
  public String getAssociatedControllerClassName() {
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
  public Class<R> getCustomResourceClass() {
    return customResourceClass;
  }
}
