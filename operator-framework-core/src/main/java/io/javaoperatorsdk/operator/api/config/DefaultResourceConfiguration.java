package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final String labelSelector;
  private final Set<String> namespaces;
  private final Class<R> resourceClass;

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      String... namespaces) {
    this(labelSelector, resourceClass,
        namespaces == null || namespaces.length == 0 ? ResourceConfiguration.DEFAULT_NAMESPACES
            : Set.of(namespaces));
  }

  public DefaultResourceConfiguration(String labelSelector, Class<R> resourceClass,
      Set<String> namespaces) {
    this.labelSelector = labelSelector;
    this.resourceClass = resourceClass;
    this.namespaces =
        namespaces == null || namespaces.isEmpty() ? ResourceConfiguration.DEFAULT_NAMESPACES
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
}
