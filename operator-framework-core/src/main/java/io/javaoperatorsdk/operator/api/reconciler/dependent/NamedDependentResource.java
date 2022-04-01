package io.javaoperatorsdk.operator.api.reconciler.dependent;

public class NamedDependentResource {

  private String name;
  private DependentResource<?, ?> dependentResource;

  public NamedDependentResource(String name, DependentResource<?, ?> dependentResource) {
    this.name = name;
    this.dependentResource = dependentResource;
  }

  public String getName() {
    return name;
  }

  public DependentResource<?, ?> getDependentResource() {
    return dependentResource;
  }
}
