package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DependentResourceSpec<T extends DependentResource<?, ?>, R, C> {

  private final Class<T> dependentResourceClass;

  private final C dependentResourceConfig;

  private final Class<R> resourceType;

  public DependentResourceSpec(Class<T> dependentResourceClass, Class<R> resourceType,
      C dependentResourceConfig) {
    this.dependentResourceClass = dependentResourceClass;
    this.dependentResourceConfig = dependentResourceConfig;
    this.resourceType = resourceType;
  }

  public Class<T> getDependentResourceClass() {
    return dependentResourceClass;
  }

  public Optional<C> getDependentResourceConfiguration() {
    return Optional.ofNullable(dependentResourceConfig);
  }

  public Class<R> getResourceType() {
    return resourceType;
  }
}
