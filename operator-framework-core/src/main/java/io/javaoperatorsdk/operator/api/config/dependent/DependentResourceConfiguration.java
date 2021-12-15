package io.javaoperatorsdk.operator.api.config.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryRetriever;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import static io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfiguration.CREATABLE_DEFAULT;
import static io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfiguration.OWNED_DEFAULT;
import static io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfiguration.UPDATABLE_DEFAULT;

public interface DependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata>
    extends ResourceConfiguration<R, DependentResourceConfiguration<R, P>> {

  default boolean creatable() {
    return CREATABLE_DEFAULT;
  }

  default boolean updatable() {
    return UPDATABLE_DEFAULT;
  }

  default boolean owned() {
    return OWNED_DEFAULT;
  }

  default PrimaryResourcesRetriever<R, P> getPrimaryResourcesRetriever() {
    return Mappers.fromOwnerReference();
  }

  default AssociatedSecondaryIdentifier<P> getAssociatedResourceIdentifier() {
    return Mappers.sameNameAndNamespace();
  }

  default AssociatedSecondaryRetriever<R, P> getAssociatedResourceRetriever() {
    return (primary, registry) -> registry.getResourceEventSourceFor(getResourceClass())
        .getResourceCache()
        .get(getAssociatedResourceIdentifier().associatedSecondaryID(primary, registry))
        .orElse(null);
  }

  default boolean skipUpdateIfUnchanged() {
    return true;
  }
}
