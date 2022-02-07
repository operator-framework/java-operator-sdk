package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@Ignore
public class KubernetesDependentResourceController<R extends HasMetadata, P extends HasMetadata>
    extends DependentResourceController<R, P, KubernetesDependentResourceConfiguration<R, P>> {

  private final KubernetesDependentResourceConfiguration<R, P> configuration;
  private KubernetesClient client;
  private InformerEventSource<R, P> informer;


  @SuppressWarnings("unchecked")
  public KubernetesDependentResourceController(DependentResource<R, P> delegate,
      KubernetesDependentResourceConfiguration<R, P> configuration) {
    super(delegate, configuration);
    // todo: check if we can validate that types actually match properly
    final var associatedPrimaries =
        (delegate instanceof PrimaryResourcesRetriever)
            ? (PrimaryResourcesRetriever<R>) delegate
            : configuration.getPrimaryResourcesRetriever();
    final var associatedSecondary =
        (delegate instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<P>) delegate
            : configuration.getAssociatedResourceIdentifier();

    final var augmented = InformerConfiguration.from(configuration)
        .withPrimaryResourcesRetriever(associatedPrimaries)
        .withAssociatedSecondaryResourceIdentifier(associatedSecondary)
        .build();
    this.configuration =
        KubernetesDependentResourceConfiguration.from(augmented, configuration.isOwned(),
            configuration.getDependentResourceClass());
  }

  @Override
  public Optional<EventSource> initEventSource(EventSourceContext<P> context) {
    this.client = context.getClient();
    informer = new InformerEventSource<>(configuration, context);
    return Optional.of(informer);
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return Optional.ofNullable(informer.getAssociated(primaryResource).orElse(null));
  }

}
