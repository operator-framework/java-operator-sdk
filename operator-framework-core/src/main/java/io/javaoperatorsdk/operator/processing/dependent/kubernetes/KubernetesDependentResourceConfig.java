package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Collections;
import java.util.Set;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

public class KubernetesDependentResourceConfig {

  private Set<String> namespaces = Collections.emptySet();
  private String labelSelector = NO_VALUE_SET;

  private boolean namespacesWereConfigured = false;

  public KubernetesDependentResourceConfig() {}

  public KubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector,
      boolean configuredNS) {
    this.namespaces = namespaces;
    this.labelSelector = labelSelector;
    this.namespacesWereConfigured = configuredNS;
  }

  public KubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector) {
    this(namespaces, labelSelector, true);
  }

  public KubernetesDependentResourceConfig setNamespaces(Set<String> namespaces) {
    this.namespacesWereConfigured = true;
    this.namespaces = namespaces;
    return this;
  }

  public KubernetesDependentResourceConfig setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  public Set<String> namespaces() {
    return namespaces;
  }

  public String labelSelector() {
    return labelSelector;
  }

  public boolean wereNamespacesConfigured() {
    return namespacesWereConfigured;
  }

}
