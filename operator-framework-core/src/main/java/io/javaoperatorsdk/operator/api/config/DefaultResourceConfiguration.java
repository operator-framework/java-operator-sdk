package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import io.fabric8.kubernetes.api.model.HasMetadata;
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
  private final UnaryOperator<R> cachePruneFunction;

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
      UnaryOperator<R> cachePruneFunction) {
    this.labelSelector = labelSelector;
    this.resourceClass = resourceClass;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.genericFilter = genericFilter;
    this.namespaces =
        namespaces == null || namespaces.isEmpty() ? DEFAULT_NAMESPACES_SET
            : namespaces;
    this.cachePruneFunction = cachePruneFunction;
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
