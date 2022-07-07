package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.filter.EventFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

// TODO: unify with ResourceConfiguration?
public class KubernetesDependentResourceConfig<R> {

  private Set<String> namespaces = Constants.SAME_AS_CONTROLLER_NAMESPACES_SET;
  private String labelSelector = NO_VALUE_SET;
  private boolean namespacesWereConfigured = false;
  private EventFilter<R> filter;

  public KubernetesDependentResourceConfig() {}

  @SuppressWarnings("rawtypes")
  public KubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector,
      boolean configuredNS, EventFilter<R> filter) {
    this.namespaces = namespaces;
    this.labelSelector = labelSelector;
    this.namespacesWereConfigured = configuredNS;
    this.filter = filter;
  }

  public KubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector) {
    this(namespaces, labelSelector, true, EventFilter.ACCEPTS_ALL);
  }

  public KubernetesDependentResourceConfig<R> setNamespaces(Set<String> namespaces) {
    this.namespacesWereConfigured = true;
    this.namespaces = namespaces;
    return this;
  }

  public KubernetesDependentResourceConfig<R> setLabelSelector(String labelSelector) {
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

  public EventFilter<R> getFilter() {
    return filter;
  }
}
