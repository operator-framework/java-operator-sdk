package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT;

public class DefaultKubernetesDependentResourceConfig
    implements KubernetesDependentResourceConfig {

  private boolean addOwnerReference = ADD_OWNER_REFERENCE_DEFAULT;
  private String[] namespaces = new String[0];
  private String labelSelector = EMPTY_STRING;

  public DefaultKubernetesDependentResourceConfig() {}

  public DefaultKubernetesDependentResourceConfig setAddOwnerReference(
      boolean addOwnerReference) {
    this.addOwnerReference = addOwnerReference;
    return this;
  }

  public DefaultKubernetesDependentResourceConfig setNamespaces(String[] namespaces) {
    this.namespaces = namespaces;
    return this;
  }

  public DefaultKubernetesDependentResourceConfig setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  @Override
  public boolean addOwnerReference() {
    return addOwnerReference;
  }

  @Override
  public String[] namespaces() {
    return namespaces;
  }

  @Override
  public String labelSelector() {
    return labelSelector;
  }
}
