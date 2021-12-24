package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public class KubernetesDependentResourceController<R extends HasMetadata, P extends HasMetadata>
    extends DependentResourceController<R, P> {
  private final InformerConfiguration<R, P> configuration;
  private final boolean owned;
  private KubernetesClient client;
  private InformerEventSource<R, P> informer;


  public KubernetesDependentResourceController(DependentResource<R, P> delegate,
      InformerConfiguration<R, P> configuration, boolean owned) {
    super(delegate);
    // todo: check if we can validate that types actually match properly
    final var associatedPrimaries =
        (delegate instanceof PrimaryResourcesRetriever)
            ? (PrimaryResourcesRetriever<R>) delegate
            : configuration.getPrimaryResourcesRetriever();
    final var associatedSecondary =
        (delegate instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<P>) delegate
            : configuration.getAssociatedResourceIdentifier();

    this.configuration = InformerConfiguration.from(configuration)
        .withPrimaryResourcesRetriever(associatedPrimaries)
        .withAssociatedSecondaryResourceIdentifier(associatedSecondary)
        .build();
    this.owned = owned;
  }

  @Override
  protected Persister<R, P> initPersister(DependentResource<R, P> delegate) {
    return (delegate instanceof Persister) ? (Persister<R, P>) delegate : this;
  }

  @Override
  public String descriptionFor(R resource) {
    return String.format("'%s' %s dependent in namespace %s", resource.getMetadata().getName(),
        resource.getFullResourceName(),
        resource.getMetadata().getNamespace());
  }

  @Override
  public EventSource initEventSource(EventSourceContext<P> context) {
    this.client = context.getClient();
    informer = new InformerEventSource<>(configuration, context);
    return informer;
  }

  @Override
  public void createOrReplace(R dependentResource, Context context) {
    client.resource(dependentResource).createOrReplace();
  }

  @Override
  public R getFor(P primary, Context context) {
    return informer.getAssociated(primary).orElse(null);
  }

  public boolean owned() {
    return owned;
  }
}
