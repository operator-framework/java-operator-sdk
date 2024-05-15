package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration.DEFAULT_FOLLOW_CONTROLLER_NAMESPACES_ON_CHANGE;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.SAME_AS_CONTROLLER_NAMESPACES_SET;

public final class KubernetesDependentInformerConfigBuilder<R extends HasMetadata> {

  private String name;
  private Set<String> namespaces = SAME_AS_CONTROLLER_NAMESPACES_SET;
  private boolean followControllerNamespacesOnChange =
      DEFAULT_FOLLOW_CONTROLLER_NAMESPACES_ON_CHANGE;
  private String labelSelector;
  private OnAddFilter<? super R> onAddFilter;
  private OnUpdateFilter<? super R> onUpdateFilter;
  private OnDeleteFilter<? super R> onDeleteFilter;
  private GenericFilter<? super R> genericFilter;
  private ItemStore<R> itemStore;
  private Long informerListLimit;

  public KubernetesDependentInformerConfigBuilder() {}

  public KubernetesDependentInformerConfigBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withNamespaces(Set<String> namespaces) {
    this.namespaces = namespaces;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withFollowControllerNamespacesOnChange(
      boolean followControllerNamespacesOnChange) {
    this.followControllerNamespacesOnChange = followControllerNamespacesOnChange;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withOnAddFilter(
      OnAddFilter<? super R> onAddFilter) {
    this.onAddFilter = onAddFilter;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withOnUpdateFilter(
      OnUpdateFilter<? super R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withOnDeleteFilter(
      OnDeleteFilter<? super R> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withGenericFilter(
      GenericFilter<? super R> genericFilter) {
    this.genericFilter = genericFilter;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withItemStore(ItemStore<R> itemStore) {
    this.itemStore = itemStore;
    return this;
  }

  public KubernetesDependentInformerConfigBuilder withInformerListLimit(Long informerListLimit) {
    this.informerListLimit = informerListLimit;
    return this;
  }

  public KubernetesDependentInformerConfig build() {
    return new KubernetesDependentInformerConfig(name, namespaces,
        followControllerNamespacesOnChange,
        labelSelector, onAddFilter, onUpdateFilter, onDeleteFilter, genericFilter, itemStore,
        informerListLimit);
  }
}
