package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;


public class KubernetesDependentInformerConfig<R extends HasMetadata> {

  private String name;
  private Set<String> namespaces;
  private boolean followControllerNamespacesOnChange;
  private String labelSelector;
  private OnAddFilter<? super R> onAddFilter;
  private OnUpdateFilter<? super R> onUpdateFilter;
  private OnDeleteFilter<? super R> onDeleteFilter;
  private GenericFilter<? super R> genericFilter;
  private ItemStore<R> itemStore;
  private Long informerListLimit;

  public KubernetesDependentInformerConfig(String name, Set<String> namespaces,
      boolean followControllerNamespacesOnChange,
      String labelSelector, OnAddFilter<? super R> onAddFilter,
      OnUpdateFilter<? super R> onUpdateFilter, OnDeleteFilter<? super R> onDeleteFilter,
      GenericFilter<? super R> genericFilter, ItemStore<R> itemStore, Long informerListLimit) {
    this.name = name;
    this.namespaces = namespaces;
    this.followControllerNamespacesOnChange = followControllerNamespacesOnChange;
    this.labelSelector = labelSelector;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.onDeleteFilter = onDeleteFilter;
    this.genericFilter = genericFilter;
    this.itemStore = itemStore;
    this.informerListLimit = informerListLimit;
  }

  public String getName() {
    return name;
  }

  public Set<String> getNamespaces() {
    return namespaces;
  }

  public boolean isFollowControllerNamespacesOnChange() {
    return followControllerNamespacesOnChange;
  }

  public String getLabelSelector() {
    return labelSelector;
  }

  public OnAddFilter<? super R> getOnAddFilter() {
    return onAddFilter;
  }

  public OnUpdateFilter<? super R> getOnUpdateFilter() {
    return onUpdateFilter;
  }

  public OnDeleteFilter<? super R> getOnDeleteFilter() {
    return onDeleteFilter;
  }

  public GenericFilter<? super R> getGenericFilter() {
    return genericFilter;
  }

  public ItemStore<R> getItemStore() {
    return itemStore;
  }

  public Long getInformerListLimit() {
    return informerListLimit;
  }

  public void updateInformerConfigBuilder(
      InformerConfiguration.InformerConfigurationBuilder<R> builder) {
    builder.withName(name);
    builder.withNamespaces(namespaces);
    builder.followControllerNamespacesOnChange(followControllerNamespacesOnChange);
    builder.withLabelSelector(labelSelector);
    builder.withItemStore(itemStore);
    builder.withOnAddFilter(onAddFilter);
    builder.withOnUpdateFilter(onUpdateFilter);
    builder.withOnDeleteFilter(onDeleteFilter);
    builder.withGenericFilter(genericFilter);
    builder.withInformerListLimit(informerListLimit);
  }

}
