package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT;

public class DefaultKubernetesDependentResourceConfigService
    implements KubernetesDependentResourceConfigService {

  private boolean addOwnerReference = ADD_OWNER_REFERENCE_DEFAULT;
  private String[] namespaces = new String[0];
  private String labelSelector = EMPTY_STRING;

  public DefaultKubernetesDependentResourceConfigService() {}

  public DefaultKubernetesDependentResourceConfigService setAddOwnerReference(
      boolean addOwnerReference) {
    this.addOwnerReference = addOwnerReference;
    return this;
  }

  public DefaultKubernetesDependentResourceConfigService setNamespaces(String[] namespaces) {
    this.namespaces = namespaces;
    return this;
  }

  public DefaultKubernetesDependentResourceConfigService setLabelSelector(String labelSelector) {
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
