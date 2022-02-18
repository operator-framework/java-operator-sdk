package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT;

public class KubernetesDependentResourceConfig extends InformerConfig {

  private boolean addOwnerReference = ADD_OWNER_REFERENCE_DEFAULT;
  private boolean editOnly = false;

  public KubernetesDependentResourceConfig() {}

  public KubernetesDependentResourceConfig(boolean addOwnerReference,
      boolean editOnly, String[] namespaces,
      String labelSelector, ConfigurationService configurationService) {
    super(namespaces, labelSelector, configurationService);
    this.addOwnerReference = addOwnerReference;
    this.editOnly = editOnly;
  }

  public KubernetesDependentResourceConfig setAddOwnerReference(
      boolean addOwnerReference) {
    this.addOwnerReference = addOwnerReference;
    return this;
  }

  public KubernetesDependentResourceConfig setEditOnly(boolean editOnly) {
    this.editOnly = editOnly;
    return this;
  }

  public boolean isEditOnly() {
    return editOnly;
  }

  public boolean addOwnerReference() {
    return addOwnerReference;
  }


}
