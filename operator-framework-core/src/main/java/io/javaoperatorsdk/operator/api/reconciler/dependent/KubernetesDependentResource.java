package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependent;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
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
    extends AbstractDependentResource<R, P, KubernetesDependentResourceConfiguration<R, P>>
    implements KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected KubernetesClient client;
  private InformerEventSource<R, P> informerEventSource;
  private DesiredSupplier<R, P> desiredSupplier;
  private KubernetesDependentResourceConfiguration<R, P> configuration;

  public KubernetesDependentResource() {
    this(null);
  }

  public KubernetesDependentResource(DesiredSupplier<R, P> desiredSupplier) {
    this.desiredSupplier = desiredSupplier;
  }

  public void configureWith(KubernetesDependentResourceConfiguration<R, P> config) {
    configureWith(config.getConfigurationService(), config.getLabelSelector(),
        config.getNamespaces(), config.isOwned());
  }

  @SuppressWarnings("unchecked")
  private void configureWith(ConfigurationService service, String labelSelector,
      Set<String> namespaces, boolean owned) {
    final var primaryResourcesRetriever =
        (this instanceof PrimaryResourcesRetriever) ? (PrimaryResourcesRetriever<R>) this
            : Mappers.fromOwnerReference();
    final AssociatedSecondaryResourceIdentifier<P> secondaryResourceIdentifier =
        (this instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<P>) this
            : ResourceID::fromResource;
    InformerConfiguration<R, P> ic =
        InformerConfiguration.from(service, resourceType())
            .withLabelSelector(labelSelector)
            .withNamespaces(namespaces)
            .withPrimaryResourcesRetriever(primaryResourcesRetriever)
            .withAssociatedSecondaryResourceIdentifier(secondaryResourceIdentifier)
            .build();
    configuration = KubernetesDependentResourceConfiguration.from(ic, owned, getClass());
    informerEventSource = new InformerEventSource<>(ic, client);
  }

  protected void beforeCreateOrUpdate(R desired, P primary) {
    if (configuration.isOwned()) {
      desired.addOwnerReference(primary);
    }
  }

  @Override
  protected boolean match(R actual, R target, Context context) {
    return ReconcilerUtils.specsEqual(actual, target);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R create(R target, P primary, Context context) {
    log.debug("Creating target resource with type: " +
        "{}, with id: {}", target.getClass(), ResourceID.fromResource(target));
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    return client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .create(target);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R update(R actual, R target, P primary, Context context) {
    log.debug("Updating target resource with type: {}, with id: {}", target.getClass(),
        ResourceID.fromResource(target));
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    return client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .replace(target);
  }

  @Override
  public Optional<EventSource> eventSource(EventSourceContext<P> context) {
    if (informerEventSource == null) {
      configureWith(context.getConfigurationService(), null, null,
          KubernetesDependent.OWNED_DEFAULT);
      log.warn("Using default configuration for " + resourceType().getSimpleName()
          + " KubernetesDependentResource, call configureWith to provide configuration");
    }
    return Optional.of(informerEventSource);
  }

  public KubernetesDependentResource<R, P> setInformerEventSource(
      InformerEventSource<R, P> informerEventSource) {
    this.informerEventSource = informerEventSource;
    return this;
  }

  @Override
  public void delete(P primary, Context context) {
    // todo: do we need explicit delete? If yes, add it to configuration
    var resource = getResource(primary);
    resource.ifPresent(r -> client.resource(r).delete());
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return informerEventSource.getAssociated(primaryResource);
  }

  @Override
  public void setKubernetesClient(KubernetesClient client) {
    this.client = client;
  }

  @Override
  protected R desired(P primary, Context context) {
    return desiredSupplier.getDesired(primary, context);
  }
}
