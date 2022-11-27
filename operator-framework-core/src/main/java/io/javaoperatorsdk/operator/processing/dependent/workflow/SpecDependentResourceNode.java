package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
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

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void resolve(KubernetesClient client, List<DependentResourceSpec> dependentResources) {
    final var spec = dependentResources.stream()
        .filter(drs -> drs.getName().equals(getName()))
        .findFirst().orElseThrow();
    setDependentResource(ManagedWorkflowSupport.instance().createAndConfigureFrom(spec, client));
  }
}
