package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.EventFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final String labelSelector;
  private final Set<String> namespaces;
  private final Class<R> resourceClass;
  private final EventFilter<R> filter;

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      EventFilter<R> filter, String... namespaces) {
    this(labelSelector, resourceClass, filter,
        namespaces == null || namespaces.length == 0 ? DEFAULT_NAMESPACES_SET
            : Set.of(namespaces));
  }

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      EventFilter<R> filter, Set<String> namespaces) {
    this.labelSelector = labelSelector;
    this.resourceClass = resourceClass;
    this.filter = filter != null ? filter : EventFilter.ACCEPTS_ALL;
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

  public EventFilter<R> getFilter() {
    return filter;
  }
}
