package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.CleanupCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReadyCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReconcileCondition;

public class DependentResourceNode<R, P extends HasMetadata> {

  private final DependentResource<R, P> dependentResource;
  private ReconcileCondition<P> reconcileCondition;
  private CleanupCondition cleanupCondition;
  private ReadyCondition<R, P> readyCondition;
  private List<DependentResourceNode> dependsOn = new ArrayList<>(1);

  public DependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      ReconcileCondition<P> reconcileCondition) {
    this(dependentResource, reconcileCondition, null);
  }

  public DependentResourceNode(DependentResource<R, P> dependentResource,
      ReconcileCondition<P> reconcileCondition, CleanupCondition cleanupCondition) {
    this.dependentResource = dependentResource;
    this.reconcileCondition = reconcileCondition;
    this.cleanupCondition = cleanupCondition;
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  public Optional<ReconcileCondition<P>> getReconcileCondition() {
    return Optional.ofNullable(reconcileCondition);
  }

  public Optional<CleanupCondition> getCleanupCondition() {
    return Optional.ofNullable(cleanupCondition);
  }

  public void setDependsOn(List<DependentResourceNode> dependsOn) {
    this.dependsOn = dependsOn;
  }

  public List<DependentResourceNode> getDependsOn() {
    return dependsOn;
  }

  public void addDependsOnRelation(DependentResourceNode node) {
    dependsOn.add(node);
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" +
        "dependentResource=" + dependentResource +
        '}';
  }

  public DependentResourceNode<R, P> setReconcileCondition(
      ReconcileCondition<P> reconcileCondition) {
    this.reconcileCondition = reconcileCondition;
    return this;
  }

  public DependentResourceNode<R, P> setCleanupCondition(CleanupCondition cleanupCondition) {
    this.cleanupCondition = cleanupCondition;
    return this;
  }

  public Optional<ReadyCondition<R, P>> getReadyCondition() {
    return Optional.ofNullable(readyCondition);
  }

  public DependentResourceNode<R, P> setReadyCondition(ReadyCondition<R, P> readyCondition) {
    this.readyCondition = readyCondition;
    return this;
  }
}
