package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final Class<R> resourceClass;
  private final String resourceTypeName;
  private final OnAddFilter<R> onAddFilter;
  private final OnUpdateFilter<R> onUpdateFilter;
  private final GenericFilter<R> genericFilter;
  private final String labelSelector;
  private final Set<String> namespaces;
  private final UnaryOperator<R> cachePruneFunction;

  protected DefaultResourceConfiguration(Class<R> resourceClass,
      Set<String> namespaces, String labelSelector, OnAddFilter<R> onAddFilter,
      OnUpdateFilter<R> onUpdateFilter, GenericFilter<R> genericFilter,
      UnaryOperator<R> cachePruneFunction) {
    this.resourceClass = resourceClass;
    this.resourceTypeName = ReconcilerUtils.getResourceTypeName(resourceClass);
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.genericFilter = genericFilter;

    this.namespaces = ResourceConfiguration.ensureValidNamespaces(namespaces);
    this.labelSelector = ResourceConfiguration.ensureValidLabelSelector(labelSelector);
    this.cachePruneFunction = cachePruneFunction;
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
  public Optional<UnaryOperator<R>> cachePruneFunction() {
    return Optional.ofNullable(this.cachePruneFunction);
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
