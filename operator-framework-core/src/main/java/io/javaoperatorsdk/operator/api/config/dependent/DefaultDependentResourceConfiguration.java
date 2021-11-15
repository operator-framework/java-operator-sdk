package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;

public class DefaultDependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata>
    extends DefaultResourceConfiguration<R, DependentResourceConfiguration<R, P>>
    implements DependentResourceConfiguration<R, P> {
  private final boolean creatable;
  private final boolean updatable;
  private final boolean owned;
  private final PrimaryResourcesRetriever<R, P> associatedPrimaries;
  private final AssociatedSecondaryIdentifier<P> associatedSecondary;
  private final boolean skipUpdateIfUnchanged;

  public DefaultDependentResourceConfiguration(String crdName, Class<R> resourceClass,
      Set<String> namespaces, String labelSelector,
      ConfigurationService service, boolean creatable, boolean updatable, boolean owned,
      PrimaryResourcesRetriever<R, P> associatedPrimaries,
      AssociatedSecondaryIdentifier<P> associatedSecondary, boolean skipUpdateIfUnchanged) {
    super(crdName, resourceClass, namespaces, labelSelector, service);
    this.creatable = creatable;
    this.updatable = updatable;
    this.owned = owned;
    this.associatedPrimaries = associatedPrimaries == null
        ? DependentResourceConfiguration.super.getPrimaryResourcesRetriever()
        : associatedPrimaries;
    this.associatedSecondary = associatedSecondary == null
        ? DependentResourceConfiguration.super.getAssociatedResourceIdentifier()
        : associatedSecondary;
    this.skipUpdateIfUnchanged = skipUpdateIfUnchanged;
  }

  @Override
  public boolean creatable() {
    return creatable;
  }

  @Override
  public boolean updatable() {
    return updatable;
  }

  @Override
  public boolean owned() {
    return owned;
  }

  @Override
  public PrimaryResourcesRetriever<R, P> getPrimaryResourcesRetriever() {
    return associatedPrimaries;
  }

  @Override
  public AssociatedSecondaryIdentifier<P> getAssociatedResourceIdentifier() {
    return associatedSecondary;
  }

  @Override
  public boolean skipUpdateIfUnchanged() {
    return skipUpdateIfUnchanged;
  }
}
