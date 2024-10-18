package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashSet;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

class DependentResourceNodePrecursor<R, P extends HasMetadata> extends WorkflowNodePrecursor<R, P> {
  private final Set<String> dependsOn = new HashSet<>();

  DependentResourceNodePrecursor(DependentResourceNode<R, P> dependentResource) {
    super(dependentResource);
  }

  @Override
  public Class<? extends DependentResource<R, P>> getDependentResourceClass() {
    return super.getDependentResourceClass();
  }

  @Override
  public Set<String> dependsOnAsNames() {
    return dependsOn;
  }

  void addDependsOn(String dependsOn) {
    this.dependsOn.add(dependsOn);
  }
}
