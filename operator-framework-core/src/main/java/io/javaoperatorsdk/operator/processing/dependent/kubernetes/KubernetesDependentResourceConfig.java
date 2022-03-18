package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT;

public class KubernetesDependentResourceConfig {

  private boolean addOwnerReference = ADD_OWNER_REFERENCE_DEFAULT;
  private String[] namespaces = new String[0];
  private String labelSelector = EMPTY_STRING;

  public KubernetesDependentResourceConfig() {}

  public KubernetesDependentResourceConfig(boolean addOwnerReference, String[] namespaces,
      String labelSelector) {
    this.addOwnerReference = addOwnerReference;
    this.namespaces = namespaces;
    this.labelSelector = labelSelector;
  }

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

  public boolean addOwnerReference() {
    return addOwnerReference;
  }

  public String[] namespaces() {
    return namespaces;
  }

  public String labelSelector() {
    return labelSelector;
  }
}
