package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

@SuppressWarnings("rawtypes")
public interface DependentResourceNode<R, P extends HasMetadata> {

  Optional<Condition<R, P>> getReconcilePrecondition();

  Optional<Condition<R, P>> getDeletePostcondition();

  List<? extends DependentResourceNode> getDependsOn();

  void addDependsOnRelation(DependentResourceNode node);

  Optional<Condition<R, P>> getReadyPostcondition();

  List<? extends DependentResourceNode> getParents();

  void addParent(DependentResourceNode parent);

  String getName();

  default void resolve(KubernetesClient client, ControllerConfiguration<P> configuration) {}
}
