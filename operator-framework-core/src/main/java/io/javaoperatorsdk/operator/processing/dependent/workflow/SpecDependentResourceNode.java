package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

@SuppressWarnings("rawtypes")
public class SpecDependentResourceNode<R, P extends HasMetadata>
    implements DependentResourceNode<R, P> {

  private final DependentResourceSpec<R, P, ?> spec;
  private final List<DependentResourceNode> dependsOn = new LinkedList<>();
  private final List<DependentResourceNode> parents = new LinkedList<>();

  public SpecDependentResourceNode(DependentResourceSpec<R, P, ?> spec) {
    this.spec = spec;
  }

  @Override
  public Optional<Condition<R, P>> getReconcilePrecondition() {
    return Optional.ofNullable(spec.getReconcileCondition());
  }

  @Override
  public Optional<Condition<R, P>> getDeletePostcondition() {
    return Optional.ofNullable(spec.getDeletePostCondition());
  }

  @Override
  public List<? extends DependentResourceNode> getDependsOn() {
    return dependsOn;
  }

  @Override
  public void addDependsOnRelation(DependentResourceNode node) {
    node.addParent(this);
    dependsOn.add(node);
  }

  @Override
  public Optional<Condition<R, P>> getReadyPostcondition() {
    return Optional.ofNullable(spec.getReadyCondition());
  }

  @Override
  public List<? extends DependentResourceNode> getParents() {
    return parents;
  }


  @Override
  public void addParent(DependentResourceNode parent) {
    parents.add(parent);
  }

  @Override
  public String getName() {
    return spec.getName();
  }
}
