package io.javaoperatorsdk.operator.api.config;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final Class<R> resourceClass;
  private final String resourceTypeName;
  private final Optional<OnAddFilter<R>> onAddFilter;
  private final Optional<OnUpdateFilter<R>> onUpdateFilter;
  private final Optional<GenericFilter<R>> genericFilter;

  private String labelSelector;
  private Set<String> namespaces;

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      OnAddFilter<R> onAddFilter, OnUpdateFilter<R> onUpdateFilter, GenericFilter<R> genericFilter,
      String... namespaces) {
    this(labelSelector, resourceClass, onAddFilter, onUpdateFilter, genericFilter,
        namespaces == null || namespaces.length == 0 ? DEFAULT_NAMESPACES_SET
            : Set.of(namespaces));
  }

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      OnAddFilter<R> onAddFilter, OnUpdateFilter<R> onUpdateFilter, GenericFilter<R> genericFilter,
      Set<String> namespaces) {
    this(resourceClass, onAddFilter, onUpdateFilter, genericFilter, namespaces);
    setLabelSelector(labelSelector);
  }

  protected DefaultResourceConfiguration(Class<R> resourceClass,
      OnAddFilter<R> onAddFilter, OnUpdateFilter<R> onUpdateFilter, GenericFilter<R> genericFilter,
      Set<String> namespaces) {
    this.resourceClass = resourceClass;
    this.resourceTypeName = ReconcilerUtils.getResourceTypeName(resourceClass);
    this.onAddFilter = Optional.ofNullable(onAddFilter);
    this.onUpdateFilter = Optional.ofNullable(onUpdateFilter);
    this.genericFilter = Optional.ofNullable(genericFilter);

    setNamespaces(namespaces);
  }

  @Override
  public String getResourceTypeName() {
    return resourceTypeName;
  }

  @Override
  public String getLabelSelector() {
    return labelSelector;
  }

  protected void setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
  }

  @Override
  public Set<String> getNamespaces() {
    return namespaces;
  }

  protected void setNamespaces(Collection<String> namespaces) {
    if (namespaces != null && !namespaces.isEmpty()) {
      this.namespaces = Set.copyOf(namespaces);
    } else {
      this.namespaces = Constants.DEFAULT_NAMESPACES_SET;
    }
  }

  @Override
  public Class<R> getResourceClass() {
    return resourceClass;
  }

  @Override
  public Optional<OnAddFilter<R>> onAddFilter() {
    return onAddFilter;
  }

  @Override
  public Optional<OnUpdateFilter<R>> onUpdateFilter() {
    return onUpdateFilter;
  }

  public Optional<GenericFilter<R>> genericFilter() {
    return genericFilter;
  }
}
