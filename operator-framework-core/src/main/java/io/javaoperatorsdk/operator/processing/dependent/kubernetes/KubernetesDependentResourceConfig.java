package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;

public class KubernetesDependentResourceConfig {

  private String[] namespaces = new String[0];
  private String labelSelector = EMPTY_STRING;

  public KubernetesDependentResourceConfig() {}

  public KubernetesDependentResourceConfig(String[] namespaces,
      String labelSelector) {
    this.namespaces = namespaces;
    this.labelSelector = labelSelector;
  }

  public KubernetesDependentResourceConfig setNamespaces(String[] namespaces) {
    this.namespaces = namespaces;
    return this;
  }

  public KubernetesDependentResourceConfig setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  public String[] namespaces() {
    return namespaces;
  }

  public String labelSelector() {
    return labelSelector;
  }
}
