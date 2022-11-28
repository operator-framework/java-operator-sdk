package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceReferencer;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;

class SpecDependentResourceNode<R, P extends HasMetadata>
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

    final DependentResource<R, P> dependentResource = spec.getDependentResource();

    if (dependentResource instanceof KubernetesClientAware) {
      ((KubernetesClientAware) dependentResource).setKubernetesClient(client);
    }

    spec.getUseEventSourceWithName()
        .ifPresent(esName -> {
          final var name = (String) esName;
          if (dependentResource instanceof EventSourceReferencer) {
            ((EventSourceReferencer) dependentResource).useEventSourceWithName(name);
          } else {
            throw new IllegalStateException(
                "DependentResource " + spec + " wants to use EventSource named " + name
                    + " but doesn't implement support for this feature by implementing "
                    + EventSourceReferencer.class.getSimpleName());
          }
        });

    setDependentResource(dependentResource);
  }
}
