package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;

public interface KubernetesDependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata>
    extends InformerConfiguration<R, P>, DependentResourceConfiguration<R, P> {

  class DefaultKubernetesDependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata>
      extends DefaultInformerConfiguration<R, P>
      implements KubernetesDependentResourceConfiguration<R, P> {

    private final boolean owned;
    private final Class<DependentResource<R, P>> dependentResourceClass;

    protected DefaultKubernetesDependentResourceConfiguration(
        ConfigurationService service,
        String labelSelector, Class<R> resourceClass,
        PrimaryResourcesRetriever<R> secondaryToPrimaryResourcesIdSet,
        AssociatedSecondaryResourceIdentifier<P> associatedWith,
        boolean skipUpdateEventPropagationIfNoChange, Set<String> namespaces, boolean owned,
        Class<DependentResource<R, P>> dependentResourceClass) {
      super(service, labelSelector, resourceClass, secondaryToPrimaryResourcesIdSet, associatedWith,
          skipUpdateEventPropagationIfNoChange, namespaces);
      this.owned = owned;
      this.dependentResourceClass = dependentResourceClass;
    }

    public boolean isOwned() {
      return owned;
    }

    @Override
    public Class<? extends DependentResource<R, P>> getDependentResourceClass() {
      return dependentResourceClass;
    }

    @Override
    public Class<R> getResourceClass() {
      return super.getResourceClass();
    }
  }

  static <R extends HasMetadata, P extends HasMetadata> KubernetesDependentResourceConfiguration<R, P> from(
      InformerConfiguration<R, P> cfg, boolean owned,
      Class<? extends DependentResource> dependentResourceClass) {
    return new DefaultKubernetesDependentResourceConfiguration<R, P>(cfg.getConfigurationService(),
        cfg.getLabelSelector(), cfg.getResourceClass(), cfg.getPrimaryResourcesRetriever(),
        cfg.getAssociatedResourceIdentifier(), cfg.isSkipUpdateEventPropagationIfNoChange(),
        cfg.getNamespaces(), owned,
        (Class<DependentResource<R, P>>) dependentResourceClass);
  }

  boolean isOwned();

  @Override
  default Class<R> getResourceClass() {
    return InformerConfiguration.super.getResourceClass();
  }
}
