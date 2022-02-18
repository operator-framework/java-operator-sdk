package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GenericDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends KubernetesDependentResourceBase<R, P, KubernetesDependentResourceConfig>
    implements GenericDependentResource<R, P>, KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected ResourceMatcher resourceMatcher;
  protected KubernetesClient client;
  private boolean addOwnerReference;
  private boolean editOnly = false;


  @Override
  public void configureWith(KubernetesDependentResourceConfig config) {
    super.configureWith(config);
    configureWith(config.getConfigurationService(), config.labelSelector(),
        Set.of(config.namespaces()), config.addOwnerReference(), config.isEditOnly());
  }

  @SuppressWarnings("unchecked")
  private void configureWith(ConfigurationService configService, String labelSelector,
      Set<String> namespaces, boolean addOwnerReference, boolean editOnly) {
    final var primaryResourcesRetriever =
        (this instanceof PrimaryResourcesRetriever) ? (PrimaryResourcesRetriever<R>) this
            : Mappers.fromOwnerReference();
    final AssociatedSecondaryResourceIdentifier<P> secondaryResourceIdentifier =
        (this instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<P>) this
            : ResourceID::fromResource;
    InformerConfiguration<R, P> ic =
        InformerConfiguration.from(configService, resourceType())
            .withLabelSelector(labelSelector)
            .withNamespaces(namespaces)
            .withPrimaryResourcesRetriever(primaryResourcesRetriever)
            .withAssociatedSecondaryResourceIdentifier(secondaryResourceIdentifier)
            .build();
    configureWith(configService, new InformerEventSource<>(ic, client), addOwnerReference,
        editOnly);
  }

  /**
   * Use to share informers between event more resources.
   *
   * @param configurationService get configs
   * @param informerEventSource informer to use
   * @param addOwnerReference to the created resource
   */
  public void configureWith(ConfigurationService configurationService,
      InformerEventSource<R, P> informerEventSource,
      boolean addOwnerReference, boolean editOnly) {
    this.informerEventSource = informerEventSource;
    this.addOwnerReference = addOwnerReference;
    this.editOnly = editOnly;
    initResourceMatcherIfNotSet(configurationService);
  }

  protected void beforeCreateOrUpdate(R desired, P primary) {
    if (addOwnerReference) {
      desired.addOwnerReference(primary);
    }
  }

  @Override
  public boolean match(R actualResource, R desiredResource, Context context) {
    return resourceMatcher.match(actualResource, desiredResource, context);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void create(R target, P primary, Context context) {
    if (editOnly) {
      return;
    }
    log.debug("Creating target resource with type: " +
        "{}, with id: {}", target.getClass(), ResourceID.fromResource(target));
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .create(target);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void update(R actual, R target, P primary, Context context) {
    log.debug("Updating target resource with type: {}, with id: {}", target.getClass(),
        ResourceID.fromResource(target));
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .replace(target);
  }

  @Override
  public EventSource eventSource(EventSourceContext<P> context) {
    initResourceMatcherIfNotSet(context.getConfigurationService());
    return super.eventSource(context);
  }

  public KubernetesDependentResource<R, P> setInformerEventSource(
      InformerEventSource<R, P> informerEventSource) {
    this.informerEventSource = informerEventSource;
    return this;
  }

  @Override
  public void delete(P primary, Context context) {
    if (!addOwnerReference) {
      var resource = getResource(primary);
      resource.ifPresent(r -> client.resource(r).delete());
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<R> getResource(P primaryResource) {
    return informerEventSource.getAssociated(primaryResource);
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  /**
   * Override this method to configure resource matcher
   *
   * @param configurationService config service to mainly access object mapper
   */
  protected void initResourceMatcherIfNotSet(ConfigurationService configurationService) {
    if (resourceMatcher == null) {
      resourceMatcher = new DesiredValueMatcher(configurationService.getObjectMapper());
    }
  }

}
