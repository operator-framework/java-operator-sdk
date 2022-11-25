package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public interface DependentResourceNode<R, P extends HasMetadata> {

  DependentResource<R, P> getDependentResource();

  Optional<Condition<R, P>> getReconcilePrecondition();

  Optional<Condition<R, P>> getDeletePostcondition();

  List<? extends DependentResourceNode> getDependsOn();

  void addDependsOnRelation(DependentResourceNode node);

  Optional<Condition<R, P>> getReadyPostcondition();

  List<? extends DependentResourceNode> getParents();

  R getSecondaryResource(P primary, Context<P> context);

  void addParent(DependentResourceNode parent);
}
