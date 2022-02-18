package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;

public class InformerConfig {

  private String[] namespaces = new String[0];
  private String labelSelector = EMPTY_STRING;
  private ConfigurationService configurationService;

  public InformerConfig() {}

  public InformerConfig(String[] namespaces, String labelSelector,
      ConfigurationService configurationService) {
    this.namespaces = namespaces;
    this.labelSelector = labelSelector;
    this.configurationService = configurationService;
  }

  public void setNamespaces(String[] namespaces) {
    this.namespaces = namespaces;
  }

  public void setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
  }

  public void setConfigurationService(
      ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public String[] namespaces() {
    return namespaces;
  }

  public String labelSelector() {
    return labelSelector;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }
}
