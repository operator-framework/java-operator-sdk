package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.javaoperatorsdk.operator.api.reconciler.Constants;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

public class KubernetesDependentResourceConfig<R> {

  private Set<String> namespaces = Constants.SAME_AS_CONTROLLER_NAMESPACES_SET;
  private String labelSelector = NO_VALUE_SET;
  private boolean namespacesWereConfigured = false;


  private Predicate<R> onAddFilter;

  private BiPredicate<R, R> onUpdateFilter;

  private BiPredicate<R, Boolean> onDeleteFilter;

  public KubernetesDependentResourceConfig() {}

  @SuppressWarnings("rawtypes")
  public KubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector,
      boolean configuredNS, Predicate<R> onAddFilter,
      BiPredicate<R, R> onUpdateFilter,
      BiPredicate<R, Boolean> onDeleteFilter) {
    this.namespaces = namespaces;
    this.labelSelector = labelSelector;
    this.namespacesWereConfigured = configuredNS;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.onDeleteFilter = onDeleteFilter;
  }

  public KubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector) {
    this(namespaces, labelSelector, true, null, null, null);
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

  @SuppressWarnings("rawtypes")
  public Predicate onAddFilter() {
    return onAddFilter;
  }

  @SuppressWarnings("rawtypes")
  public BiPredicate onUpdateFilter() {
    return onUpdateFilter;
  }

  @SuppressWarnings("rawtypes")
  public BiPredicate onDeleteFilter() {
    return onDeleteFilter;
  }
}
