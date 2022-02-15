package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DependentResourceConfig {

  private final Class<? extends DependentResource> dependentResourceClass;

  private final DependentResourceConfigService dependentResourceConfigService;

  public DependentResourceConfig(Class<? extends DependentResource> dependentResourceClass) {
    this(dependentResourceClass, null);
  }

  public DependentResourceConfig(Class<? extends DependentResource> dependentResourceClass,
      DependentResourceConfigService dependentResourceConfigService) {
    this.dependentResourceClass = dependentResourceClass;
    this.dependentResourceConfigService = dependentResourceConfigService;
  }

  public Class<? extends DependentResource> getDependentResourceClass() {
    return dependentResourceClass;
  }

  public Optional<DependentResourceConfigService> getDependentResourceConfigService() {
    return Optional.ofNullable(dependentResourceConfigService);
  }
}
