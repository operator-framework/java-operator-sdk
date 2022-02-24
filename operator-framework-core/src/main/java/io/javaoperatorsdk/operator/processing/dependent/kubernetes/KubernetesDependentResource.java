package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractDependentResource<R, P, KubernetesDependentResourceConfig>
    implements KubernetesClientAware, EventSourceProvider<P>,
    DependentResourceConfigurator<KubernetesDependentResourceConfig> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  private boolean addOwnerReference;
  protected KubernetesClient client;
  protected ClientFacade<R> clientFacade;
  protected InformerEventSource<R, P> informerEventSource;
  protected ResourceMatcher resourceMatcher;
  protected ResourceUpdatePreProcessor<R> resourceUpdatePreProcessor;

  @Override
  public void configureWith(KubernetesDependentResourceConfig config) {
    configureWith(config.getConfigurationService(), config.labelSelector(),
        Set.of(config.namespaces()), config.addOwnerReference());
  }

  @SuppressWarnings("unchecked")
  private void configureWith(ConfigurationService configService, String labelSelector,
      Set<String> namespaces, boolean addOwnerReference) {
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
    configureWith(configService, new InformerEventSource<>(ic, client), addOwnerReference);
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
      boolean addOwnerReference) {
    this.informerEventSource = informerEventSource;
    this.addOwnerReference = addOwnerReference;
    initResourceMatcherAndUpdatePreProcessorIfNotSet(configurationService);
  }

  protected void beforeCreate(R desired, P primary) {
    if (addOwnerReference) {
      desired.addOwnerReference(primary);
    }
  }

  @Override
  protected boolean match(R actualResource, R desiredResource, Context context) {
    return resourceMatcher.match(actualResource, desiredResource, context);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R create(R target, P primary, Context context) {
    log.debug("Creating target resource with type: " +
        "{}, with id: {}", target.getClass(), ResourceID.fromResource(target));
    beforeCreate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    var newResource =
        clientFacade.createResource(target, target.getMetadata().getNamespace(), targetClass);
    informerEventSource.handleRecentResourceAdd(newResource);
    return newResource;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R update(R actual, R target, P primary, Context context) {
    log.debug("Updating target resource with type: {}, with id: {}", target.getClass(),
        ResourceID.fromResource(target));
    Class<R> targetClass = (Class<R>) target.getClass();
    var updatedActual = resourceUpdatePreProcessor.replaceSpecOnActual(actual, target);
    R updatedResource = clientFacade.replaceResource(updatedActual,
        target.getMetadata().getNamespace(), targetClass);
    informerEventSource.handleRecentResourceUpdate(updatedResource,
        actual.getMetadata().getResourceVersion());
    return updatedResource;
  }

  @Override
  public EventSource eventSource(EventSourceContext<P> context) {
    initResourceMatcherAndUpdatePreProcessorIfNotSet(context.getConfigurationService());
    if (informerEventSource == null) {
      configureWith(context.getConfigurationService(), null, null,
          KubernetesDependent.DEFAULT_ADD_OWNER_REFERENCE);
      log.warn("Using default configuration for " + resourceType().getSimpleName()
          + " KubernetesDependentResource, call configureWith to provide configuration");
    }
    return informerEventSource;
  }

  @Override
  public void delete(P primary, Context context) {
    if (!addOwnerReference) {
      var resource = getResource(primary);
      resource.ifPresent(r -> clientFacade.deleteResource(r));
    }
  }

  @SuppressWarnings("unchecked")
  protected Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromExtendedClass(getClass());
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return informerEventSource.getAssociated(primaryResource);
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
    this.clientFacade = new ClientFacade<>(kubernetesClient);
  }

  /**
   * Override this method to configure resource matcher
   *
   * @param configurationService config service to mainly access object mapper
   */
  protected void initResourceMatcherAndUpdatePreProcessorIfNotSet(
      ConfigurationService configurationService) {
    if (resourceMatcher == null) {
      resourceMatcher = new DesiredValueMatcher(configurationService.getObjectMapper());
    }
    if (resourceUpdatePreProcessor == null) {
      resourceUpdatePreProcessor =
          new ResourceUpdatePreProcessor<>(configurationService.getResourceCloner());
    }
  }

  public KubernetesDependentResource<R, P> setResourceMatcher(ResourceMatcher resourceMatcher) {
    this.resourceMatcher = resourceMatcher;
    return this;
  }

  public KubernetesDependentResource<R, P> setResourceUpdatePreProcessor(
      ResourceUpdatePreProcessor<R> resourceUpdatePreProcessor) {
    this.resourceUpdatePreProcessor = resourceUpdatePreProcessor;
    return this;
  }

  public static class ClientFacade<T extends HasMetadata> {
    private final KubernetesClient client;

    public ClientFacade(KubernetesClient kubernetesClient) {
      this.client = kubernetesClient;
    }

    public T createResource(T resource, String namespace, Class<T> rClass) {
      return client.resources(rClass).inNamespace(namespace).create(resource);
    }

    public T replaceResource(T resource, String namespace, Class<T> rClass) {
      return client.resources(rClass).inNamespace(namespace).replace(resource);
    }

    public void deleteResource(T resource) {
      client.resource(resource).delete();
    }
  }

}
