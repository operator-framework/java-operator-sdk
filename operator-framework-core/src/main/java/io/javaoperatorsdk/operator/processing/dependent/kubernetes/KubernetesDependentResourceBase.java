package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResourceBase<R extends HasMetadata, P extends HasMetadata, C extends InformerConfig>
    implements DependentResourceConfigurator<C>, EventSourceProvider<P>, KubernetesClientAware {

  protected InformerEventSource<R, P> informerEventSource;
  protected KubernetesClient client;

  @Override
  public void configureWith(C config) {
    configureWithInformerConfig(config);
  }

  public void configureWithInformerConfig(InformerConfig config) {
    final var primaryResourcesRetriever =
        (this instanceof PrimaryResourcesRetriever) ? (PrimaryResourcesRetriever<R>) this
            : Mappers.fromOwnerReference();
    final AssociatedSecondaryResourceIdentifier<P> secondaryResourceIdentifier =
        (this instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<P>) this
            : ResourceID::fromResource;

    var ic = InformerConfiguration
        .from(config.getConfigurationService(), resourceType())
        .withLabelSelector(config.labelSelector())
        .withNamespaces(config.namespaces())
        .withPrimaryResourcesRetriever(primaryResourcesRetriever)
        .withAssociatedSecondaryResourceIdentifier(secondaryResourceIdentifier)
        .build();
    this.informerEventSource = new InformerEventSource<>(ic, client);
  }

  protected Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromExtendedClass(getClass());
  }

  @Override
  public EventSource eventSource(EventSourceContext<P> context) {
    configureWithInformerConfig(new InformerConfig(null, null, context.getConfigurationService()));
    return informerEventSource;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }


  public Optional<R> getResource(P primaryResource) {
    return informerEventSource.getAssociated(primaryResource);
  }


}
