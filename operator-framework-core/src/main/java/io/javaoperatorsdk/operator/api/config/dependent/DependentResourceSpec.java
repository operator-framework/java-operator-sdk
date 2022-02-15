package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DependentResourceSpec<T extends DependentResource<?,?,C>,C> {

  private final Class<T> dependentResourceClass;

  private final C dependentResourceConfig;

  public DependentResourceSpec(Class<T> dependentResourceClass) {
    this(dependentResourceClass, null);
  }

  public DependentResourceSpec(Class<T> dependentResourceClass,
                               C dependentResourceConfig) {
    this.dependentResourceClass = dependentResourceClass;
    this.dependentResourceConfig = dependentResourceConfig;
  }

  public Class<T> getDependentResourceClass() {
    return dependentResourceClass;
  }

  public Optional<C> getDependentResourceConfigService() {
    return Optional.ofNullable(dependentResourceConfig);
  }
}
