package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@Ignore
public class KubernetesDependentResourceController<R extends HasMetadata, P extends HasMetadata>
    extends
    DependentResourceController<R, P, KubernetesDependentResourceConfiguration<R, P>, KubernetesDependentResource<R, P>> {

  public KubernetesDependentResourceController(KubernetesDependentResource<R, P> delegate,
      KubernetesDependentResourceConfiguration<R, P> configuration) {
    super(delegate, configuration);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void applyConfigurationToDelegate(
      KubernetesDependentResource<R, P> delegate,
      KubernetesDependentResourceConfiguration<R, P> configuration) {
    delegate.setOwned(configuration.isOwned());

    final var associatedPrimaries =
        (delegate instanceof PrimaryResourcesRetriever)
            ? (PrimaryResourcesRetriever<R>) delegate : null;

    final var associatedSecondary =
        (delegate instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<P>) delegate
            : null;

//    final var augmented = InformerConfiguration.from(configuration)
//        .withPrimaryResourcesRetriever(associatedPrimaries)
//        .withAssociatedSecondaryResourceIdentifier(associatedSecondary)
//        .build();
//    return KubernetesDependentResourceConfiguration.from(augmented, configuration.isOwned(),
//        configuration.getDependentResourceClass());

  }

  @Override
  public Optional<EventSource> eventSource(EventSourceContext<P> context) {
//    var informer = new InformerEventSource<>(getConfiguration(), context);
    // todo have this implemented with nicer abstractions
//    delegate().setInformerEventSource(informer);
    return super.eventSource(context);
  }
}
