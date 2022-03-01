package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.OperatorException;

public class ManagedDependentResourceContext {

  private List<DependentResource> dependentResources;

  public ManagedDependentResourceContext(List<DependentResource> dependentResources) {
    this.dependentResources = dependentResources;
  }

  public List<DependentResource> getDependentResources() {
    return Collections.unmodifiableList(dependentResources);
  }

  public <T extends DependentResource> T getDependentResource(Class<T> resourceClass) {
    var resourceList =
        dependentResources.stream()
            .filter(dr -> dr.getClass().equals(resourceClass))
            .collect(Collectors.toList());
    if (resourceList.isEmpty()) {
      throw new OperatorException(
          "No dependent resource found for class: " + resourceClass.getName());
    }
    if (resourceList.size() > 1) {
      throw new OperatorException(
          "More than one dependent resource found for class: " + resourceClass.getName());
    }
    return (T) resourceList.get(0);
  }
}
