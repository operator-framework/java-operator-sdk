package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerConfiguration;

public class KubernetesDependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata>
    extends InformerConfiguration<R, P> {

  private final boolean owned;

  protected KubernetesDependentResourceConfiguration(
      ConfigurationService service,
      String labelSelector, Class<R> resourceClass,
      PrimaryResourcesRetriever<R> secondaryToPrimaryResourcesIdSet,
      AssociatedSecondaryResourceIdentifier<P> associatedWith,
      boolean skipUpdateEventPropagationIfNoChange, Set<String> namespaces, boolean owned) {
    super(service, labelSelector, resourceClass, secondaryToPrimaryResourcesIdSet, associatedWith,
        skipUpdateEventPropagationIfNoChange, namespaces);
    this.owned = owned;
  }

  public static <R extends HasMetadata, P extends HasMetadata> KubernetesDependentResourceConfiguration<R, P> from(
      InformerConfiguration<R, P> cfg, boolean owned) {
    return new KubernetesDependentResourceConfiguration<>(cfg.getConfigurationService(),
        cfg.getLabelSelector(), cfg.getResourceClass(), cfg.getPrimaryResourcesRetriever(),
        cfg.getAssociatedResourceIdentifier(), cfg.isSkipUpdateEventPropagationIfNoChange(),
        cfg.getNamespaces(), owned);
  }

  public boolean isOwned() {
    return owned;
  }
}
