package io.javaoperatorsdk.operator.processing.dependent.workflow;


import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceReferencer;

@SuppressWarnings({"rawtypes", "unchecked"})
class UnresolvedDependentResourceNode<R, P extends HasMetadata>
    extends WorkflowNodePrecursor<R, P> {
  private final DependentResourceSpec<R, P, ?> spec;

  UnresolvedDependentResourceNode(DependentResourceSpec<R, P, ?> spec) {
    super(spec.getReconcileCondition(), spec.getDeletePostCondition(), spec.getReadyCondition(),
        spec.getActivationCondition());
    this.spec = spec;
  }

  void resolve(ControllerConfiguration<P> configuration) {
    if (dependentResource == null) {
      dependentResource = configuration.getConfigurationService().dependentResourceFactory()
          .createFrom(spec, configuration);

      spec.getUseEventSourceWithName()
          .ifPresent(esName -> {
            if (dependentResource instanceof EventSourceReferencer esReferencer) {
              esReferencer.useEventSourceWithName(esName);
            } else {
              throw new IllegalStateException(
                  "DependentResource " + spec + " wants to use EventSource named " + esName
                      + " but doesn't implement support for this feature by implementing "
                      + EventSourceReferencer.class.getSimpleName());
            }
          });
    }
  }

  @Override
  public DependentResource<R, P> getDependentResource() {
    if (dependentResource == null) {
      throw new IllegalStateException(
          name() + " dependent resource node should be resolved first");
    }
    return super.getDependentResource();
  }

  public String name() {
    return dependentResource != null ? super.name() : spec.getName();
  }

  @Override
  public Set<String> dependsOnAsNames() {
    return spec.getDependsOn();
  }
}
