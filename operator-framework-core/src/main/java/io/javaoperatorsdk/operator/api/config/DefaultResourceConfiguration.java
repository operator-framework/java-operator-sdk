package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import java.util.Optional;
import java.util.Set;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final Class<R> resourceClass;
  private final String resourceTypeName;
  private final OnAddFilter<? super R> onAddFilter;
  private final OnUpdateFilter<? super R> onUpdateFilter;
  private final GenericFilter<? super R> genericFilter;
  private final String labelSelector;
  private final Set<String> namespaces;
  private final ItemStore<R> itemStore;
  private final Long informerListLimit;

  protected DefaultResourceConfiguration(Class<R> resourceClass,
      Set<String> namespaces, String labelSelector, OnAddFilter<? super R> onAddFilter,
      OnUpdateFilter<? super R> onUpdateFilter, GenericFilter<? super R> genericFilter,
      ItemStore<R> itemStore, Long informerListLimit) {
    this.resourceClass = resourceClass;
    this.resourceTypeName = resourceClass.isAssignableFrom(GenericKubernetesResource.class)
        // in general this is irrelevant now for secondary resources it is used just by controller
        // where GenericKubernetesResource now does not apply
        ? GenericKubernetesResource.class.getSimpleName()
        : ReconcilerUtils.getResourceTypeName(resourceClass);
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.genericFilter = genericFilter;

    this.namespaces = ResourceConfiguration.ensureValidNamespaces(namespaces);
    this.labelSelector = ResourceConfiguration.ensureValidLabelSelector(labelSelector);
    this.itemStore = itemStore;
    this.informerListLimit = informerListLimit;
  }

  @Override
  public String getResourceTypeName() {
    return resourceTypeName;
  }

  @Override
  public String getLabelSelector() {
    return labelSelector;
  }

  @Override
  public Set<String> getNamespaces() {
    return namespaces;
  }

  @Override
  public Class<R> getResourceClass() {
    return resourceClass;
  }

  @Override
  public Optional<OnAddFilter<? super R>> onAddFilter() {
    return Optional.ofNullable(onAddFilter);
  }

  @Override
  public Optional<OnUpdateFilter<? super R>> onUpdateFilter() {
    return Optional.ofNullable(onUpdateFilter);
  }

  @Override
  public Optional<GenericFilter<? super R>> genericFilter() {
    return Optional.ofNullable(genericFilter);
  }

  @Override
  public Optional<ItemStore<R>> getItemStore() {
    return Optional.ofNullable(itemStore);
  }

  @Override
  public Optional<Long> getInformerListLimit() {
    return Optional.ofNullable(informerListLimit);
  }
}
