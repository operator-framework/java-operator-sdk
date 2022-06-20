package io.javaoperatorsdk.operator.api.config;

import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final String labelSelector;
  private final Set<String> namespaces;
  private final Class<R> resourceClass;
  private final Predicate<R> onAddFilter;
  private final BiPredicate<R, R> onUpdateFilter;

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      Predicate<R> onAddFilter,
      BiPredicate<R, R> onUpdateFilter, String... namespaces) {
    this(labelSelector, resourceClass, onAddFilter, onUpdateFilter,
        namespaces == null || namespaces.length == 0 ? DEFAULT_NAMESPACES_SET
            : Set.of(namespaces));
  }

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      Predicate<R> onAddFilter,
      BiPredicate<R, R> onUpdateFilter, Set<String> namespaces) {
    this.labelSelector = labelSelector;
    this.resourceClass = resourceClass;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.namespaces =
        namespaces == null || namespaces.isEmpty() ? DEFAULT_NAMESPACES_SET
            : namespaces;
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
  public Class<R> getResourceClass() {
    return resourceClass;
  }

  @Override
  public Predicate<R> onAddFilter() {
    return onAddFilter;
  }

  @Override
  public BiPredicate<R, R> onUpdateFilter() {
    return onUpdateFilter;
  }
}
