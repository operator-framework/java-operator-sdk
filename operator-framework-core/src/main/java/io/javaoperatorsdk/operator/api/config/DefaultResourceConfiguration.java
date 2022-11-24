package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final String labelSelector;
  private final Set<String> namespaces;
  private final Class<R> resourceClass;
  private final OnAddFilter<R> onAddFilter;
  private final OnUpdateFilter<R> onUpdateFilter;
  private final GenericFilter<R> genericFilter;
  private final ItemStore<R> itemStore;

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      OnAddFilter<R> onAddFilter,
      OnUpdateFilter<R> onUpdateFilter, GenericFilter<R> genericFilter, String... namespaces) {
    this(labelSelector, resourceClass, onAddFilter, onUpdateFilter, genericFilter,
        namespaces == null || namespaces.length == 0 ? DEFAULT_NAMESPACES_SET
            : Set.of(namespaces),
        null);
  }

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      OnAddFilter<R> onAddFilter,
      OnUpdateFilter<R> onUpdateFilter,
      GenericFilter<R> genericFilter,
      Set<String> namespaces,
      ItemStore<R> itemStore) {
    this.labelSelector = labelSelector;
    this.resourceClass = resourceClass;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.genericFilter = genericFilter;
    this.namespaces =
        namespaces == null || namespaces.isEmpty() ? DEFAULT_NAMESPACES_SET
            : namespaces;
    this.itemStore = itemStore;
  }

  @Override
  public String getResourceTypeName() {
    return ResourceConfiguration.super.getResourceTypeName();
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
  public Optional<ItemStore<R>> itemStore() {
    return Optional.ofNullable(this.itemStore);
  }

  @Override
  public Class<R> getResourceClass() {
    return resourceClass;
  }

  @Override
  public Optional<OnAddFilter<R>> onAddFilter() {
    return Optional.ofNullable(onAddFilter);
  }

  @Override
  public Optional<OnUpdateFilter<R>> onUpdateFilter() {
    return Optional.ofNullable(onUpdateFilter);
  }

  public Optional<GenericFilter<R>> genericFilter() {
    return Optional.ofNullable(genericFilter);
  }
}
