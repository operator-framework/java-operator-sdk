package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public final class KubernetesDependentResourceConfigBuilder<R> {

  private Set<String> namespaces;
  private String labelSelector;
  private boolean createResourceOnlyIfNotExistingWithSSA;
  private ResourceDiscriminator<R, ?> resourceDiscriminator;
  private Boolean useSSA;
  private OnAddFilter<R> onAddFilter;
  private OnUpdateFilter<R> onUpdateFilter;
  private OnDeleteFilter<R> onDeleteFilter;
  private GenericFilter<R> genericFilter;

  public KubernetesDependentResourceConfigBuilder() {}

  public static <R> KubernetesDependentResourceConfigBuilder<R> aKubernetesDependentResourceConfig() {
    return new KubernetesDependentResourceConfigBuilder<>();
  }

  public KubernetesDependentResourceConfigBuilder<R> withNamespaces(Set<String> namespaces) {
    this.namespaces = namespaces;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withCreateResourceOnlyIfNotExistingWithSSA(
      boolean createResourceOnlyIfNotExistingWithSSA) {
    this.createResourceOnlyIfNotExistingWithSSA = createResourceOnlyIfNotExistingWithSSA;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withResourceDiscriminator(
      ResourceDiscriminator<R, ?> resourceDiscriminator) {
    this.resourceDiscriminator = resourceDiscriminator;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withUseSSA(Boolean useSSA) {
    this.useSSA = useSSA;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withOnAddFilter(OnAddFilter<R> onAddFilter) {
    this.onAddFilter = onAddFilter;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withOnUpdateFilter(
      OnUpdateFilter<R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withOnDeleteFilter(
      OnDeleteFilter<R> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withGenericFilter(
      GenericFilter<R> genericFilter) {
    this.genericFilter = genericFilter;
    return this;
  }

  public KubernetesDependentResourceConfig<R> build() {
    return new KubernetesDependentResourceConfig<>(namespaces, labelSelector, false,
        createResourceOnlyIfNotExistingWithSSA, resourceDiscriminator, useSSA, onAddFilter,
        onUpdateFilter, onDeleteFilter, genericFilter);
  }
}
