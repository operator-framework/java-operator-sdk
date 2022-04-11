package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.CleanupCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReconcileCondition;

public class DependentResourceNode {

  private final DependentResource<?, ?> dependentResource;
  private final ReconcileCondition reconcileCondition;
  private final CleanupCondition cleanupCondition;
  private List<DependsOnRelation> dependsOnRelations = new ArrayList<>(1);

  public DependentResourceNode(DependentResource<?, ?> dependentResource) {
    this(dependentResource, null, null);
  }

  public DependentResourceNode(DependentResource<?, ?> dependentResource,
      ReconcileCondition reconcileCondition) {
    this(dependentResource, reconcileCondition, null);
  }

  public DependentResourceNode(DependentResource<?, ?> dependentResource,
      ReconcileCondition reconcileCondition, CleanupCondition cleanupCondition) {
    this.dependentResource = dependentResource;
    this.reconcileCondition = reconcileCondition;
    this.cleanupCondition = cleanupCondition;
  }

  public DependentResource getDependentResource() {
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

  @Override
  public String toString() {
    return "DependentResourceNode{" +
        "dependentResource=" + dependentResource +
        '}';
  }
}
