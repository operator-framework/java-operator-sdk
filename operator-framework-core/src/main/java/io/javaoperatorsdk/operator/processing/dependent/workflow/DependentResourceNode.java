package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.CleanupCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReconcileCondition;

public class DependentResourceNode<R, P extends HasMetadata> {

  private final DependentResource<R, P> dependentResource;
  private ReconcileCondition reconcileCondition;
  private CleanupCondition cleanupCondition;
  private List<DependsOnRelation> dependsOnRelations = new ArrayList<>(1);

  public DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      ReconcileCondition reconcileCondition) {
    this(dependentResource, reconcileCondition, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      ReconcileCondition reconcileCondition, CleanupCondition cleanupCondition) {
    this.dependentResource = dependentResource;
    this.reconcileCondition = reconcileCondition;
    this.cleanupCondition = cleanupCondition;
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  public Optional<ReconcileCondition> getReconcileCondition() {
    return Optional.ofNullable(reconcileCondition);
  }

  public Optional<CleanupCondition> getCleanupCondition() {
    return Optional.ofNullable(cleanupCondition);
  }

  public void setDependsOnRelations(List<DependsOnRelation> dependsOnRelations) {
    this.dependsOnRelations = dependsOnRelations;
  }

  public List<DependsOnRelation> getDependsOnRelations() {
    return dependsOnRelations;
  }

  public void addDependsOnRelation(DependsOnRelation dependsOnRelation) {
    dependsOnRelations.add(dependsOnRelation);
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" +
        "dependentResource=" + dependentResource +
        '}';
  }
}
