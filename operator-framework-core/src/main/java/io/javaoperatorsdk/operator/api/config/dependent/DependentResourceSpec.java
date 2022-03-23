package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Objects;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DependentResourceSpec<T extends DependentResource<?, ?>, C> {

  private final Class<T> dependentResourceClass;

  private final C dependentResourceConfig;

  private final String name;

  public DependentResourceSpec(Class<T> dependentResourceClass, C dependentResourceConfig,
      String name) {
    this.dependentResourceClass = dependentResourceClass;
    this.dependentResourceConfig = dependentResourceConfig;
    this.name = name;
  }

  public Class<T> getDependentResourceClass() {
    return dependentResourceClass;
  }

  public Optional<C> getDependentResourceConfiguration() {
    return Optional.ofNullable(dependentResourceConfig);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "DependentResourceSpec{ name='" + name +
        "', type=" + dependentResourceClass.getCanonicalName() +
        ", config=" + dependentResourceConfig + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DependentResourceSpec<?, ?> that = (DependentResourceSpec<?, ?>) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
