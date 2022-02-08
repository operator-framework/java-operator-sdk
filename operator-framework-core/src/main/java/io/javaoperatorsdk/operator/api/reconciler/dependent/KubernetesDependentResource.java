package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  private KubernetesClient client;
  private boolean manageDelete;
  private InformerEventSource<R, P> informerEventSource;

  public KubernetesDependentResource() {
    this(null, false);
  }

  public KubernetesDependentResource(KubernetesClient client) {
    this(client, false);
  }

  public KubernetesDependentResource(KubernetesClient client, boolean manageDelete) {
    this.client = client;
    this.manageDelete = manageDelete;
  }

  @Override
  protected R create(R target, Context context) {
    return client.resource(target).createOrReplace();
  }

  @Override
  protected R update(R actual, R target, Context context) {
    // todo map annotation and labels ?
    return client.resource(target).createOrReplace();
  }

  @Override
  public Optional<EventSource> eventSource(EventSourceContext<P> context) {
    if (informerEventSource != null) {
      return Optional.of(informerEventSource);
    }
    var informerConfig = initInformerConfiguration(context);
    informerEventSource = new InformerEventSource(informerConfig, context);
    return Optional.of(informerEventSource);
  }

  private InformerConfiguration<R, P> initInformerConfiguration(EventSourceContext<P> context) {
    PrimaryResourcesRetriever<R> associatedPrimaries =
        (this instanceof PrimaryResourcesRetriever) ? (PrimaryResourcesRetriever<R>) this
            : Mappers.fromOwnerReference();

    AssociatedSecondaryResourceIdentifier<R> associatedSecondary =
        (this instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<R>) this
            : (r) -> ResourceID.fromResource(r);

    return InformerConfiguration.from(context, resourceType())
        .withPrimaryResourcesRetriever(associatedPrimaries)
        .withAssociatedSecondaryResourceIdentifier(
            (AssociatedSecondaryResourceIdentifier<P>) associatedSecondary)
        .build();
  }

  public KubernetesDependentResource<R, P> withInformerEventSource(
      InformerEventSource<R, P> informerEventSource) {
    this.informerEventSource = informerEventSource;
    return this;
  }

  @Override
  public void delete(P primary, Context context) {
    if (manageDelete) {
      var resource = getResource(primary);
      resource.ifPresent(r -> client.resource(r).delete());
    }
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return informerEventSource.getAssociated(primaryResource);
  }

  public KubernetesDependentResource<R, P> setClient(KubernetesClient client) {
    this.client = client;
    return this;
  }
}
