package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

public class SpecDependentResourceNode<R, P extends HasMetadata>
    extends AbstractDependentResourceNode<R, P> {
  @SuppressWarnings("unchecked")
  public SpecDependentResourceNode(DependentResourceSpec<R, P, ?> spec) {
    super(spec.getName());
    setReadyPostcondition(spec.getReadyCondition());
    setDeletePostcondition(spec.getDeletePostCondition());
    setReconcilePrecondition(spec.getReconcileCondition());
  }
}
