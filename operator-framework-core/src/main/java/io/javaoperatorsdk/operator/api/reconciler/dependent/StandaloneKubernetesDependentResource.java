package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

// todo shorter name
public class StandaloneKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends KubernetesDependentResource<R, P> {

  private final DesiredSupplier<R, P> desiredSupplier;
  private final Class<R> resourceType;
  private AssociatedSecondaryResourceIdentifier<P> associatedSecondaryResourceIdentifier =
      (r) -> ResourceID.fromResource(r);
  private PrimaryResourcesRetriever<R> primaryResourcesRetriever = Mappers.fromOwnerReference();

  public StandaloneKubernetesDependentResource(
      Class<R> resourceType, DesiredSupplier<R, P> desiredSupplier) {
    this(null, resourceType, desiredSupplier);
  }

  public StandaloneKubernetesDependentResource(
      KubernetesClient client, Class<R> resourceType, DesiredSupplier<R, P> desiredSupplier) {
    super(client);
    this.desiredSupplier = desiredSupplier;
    this.resourceType = resourceType;
  }

  @Override
  protected R desired(P primary, Context context) {
    return desiredSupplier.getDesired(primary, context);
  }

  public Class<R> resourceType() {
    return resourceType;
  }

  public StandaloneKubernetesDependentResource<R, P> setAssociatedSecondaryResourceIdentifier(
      AssociatedSecondaryResourceIdentifier<P> associatedSecondaryResourceIdentifier) {
    this.associatedSecondaryResourceIdentifier = associatedSecondaryResourceIdentifier;
    return this;
  }

  public StandaloneKubernetesDependentResource<R, P> setPrimaryResourcesRetriever(
      PrimaryResourcesRetriever<R> primaryResourcesRetriever) {
    this.primaryResourcesRetriever = primaryResourcesRetriever;
    return this;
  }

  @Override
  protected AssociatedSecondaryResourceIdentifier<P> getDefaultAssociatedSecondaryResourceIdentifier() {
    return this.associatedSecondaryResourceIdentifier;
  }

  @Override
  protected PrimaryResourcesRetriever<R> getDefaultPrimaryResourcesRetriever() {
    return this.primaryResourcesRetriever;
  }
}
