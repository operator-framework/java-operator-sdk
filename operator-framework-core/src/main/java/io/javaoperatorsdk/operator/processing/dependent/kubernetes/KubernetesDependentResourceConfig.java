package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT;

public class KubernetesDependentResourceConfig {

  private boolean addOwnerReference = ADD_OWNER_REFERENCE_DEFAULT;
  private String[] namespaces = new String[0];
  private String labelSelector = EMPTY_STRING;
  private ConfigurationService configurationService;

  public KubernetesDependentResourceConfig() {}

  public KubernetesDependentResourceConfig setAddOwnerReference(
      boolean addOwnerReference) {
    this.addOwnerReference = addOwnerReference;
    return this;
  }

  public KubernetesDependentResourceConfig setNamespaces(String[] namespaces) {
    this.namespaces = namespaces;
    return this;
  }

  public KubernetesDependentResourceConfig setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  public KubernetesDependentResourceConfig setConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
    return this;
  }

  public boolean addOwnerReference() {
    return addOwnerReference;
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
