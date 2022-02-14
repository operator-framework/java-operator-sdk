package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface KubernetesDependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata>
    extends DependentResourceConfiguration<R, P>, ResourceConfiguration<R> {

  boolean isOwned();

  @Override
  default Class<R> getResourceClass() {
    return ResourceConfiguration.super.getResourceClass();
  }

  class DefaultKubernetesDependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata>
      extends DefaultResourceConfiguration<R>
      implements KubernetesDependentResourceConfiguration<R, P> {


    private final Class<DependentResource<R, P, KubernetesDependentResourceConfiguration<R, P>>> dependentResourceClass;
    private final boolean owned;

    protected DefaultKubernetesDependentResourceConfiguration(
        ConfigurationService configurationService,
        String labelSelector, Class<R> resourceClass,
        Set<String> namespaces, boolean owned,
        Class<DependentResource<R, P, KubernetesDependentResourceConfiguration<R, P>>> dependentResourceClass) {
      super(labelSelector, resourceClass, namespaces);
      setConfigurationService(configurationService);
      this.owned = owned;
      this.dependentResourceClass = dependentResourceClass;
    }

    @Override
    public boolean isOwned() {
      return owned;
    }

    @Override
    public Class<? extends DependentResource<R, P, KubernetesDependentResourceConfiguration<R, P>>> getDependentResourceClass() {
      return dependentResourceClass;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static <R extends HasMetadata, P extends HasMetadata> KubernetesDependentResourceConfiguration<R, P> from(
      InformerConfiguration<R, P> cfg, boolean owned,
      Class<? extends DependentResource> dependentResourceClass) {
    return new DefaultKubernetesDependentResourceConfiguration<>(cfg.getConfigurationService(),
        cfg.getLabelSelector(), cfg.getResourceClass(),
        cfg.getNamespaces(), owned,
        (Class<DependentResource<R, P, KubernetesDependentResourceConfiguration<R, P>>>) dependentResourceClass);
  }
}
