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
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractDependentResource<R, P, KubernetesDependentResourceConfig>
    implements KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected KubernetesClient client;
  protected InformerEventSource<R, P> informerEventSource;
  private boolean addOwnerReference;
  protected ResourceMatcher resourceMatcher;
  protected TemporalResourceCache<R> temporalResourceCache;

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
    initResourceMatcherIfNotSet(configurationService);
    temporalResourceCache = new TemporalResourceCache<>(informerEventSource);
  }

  protected void beforeCreateOrUpdate(R desired, P primary) {
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
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    var newResource = client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .create(target);
    temporalResourceCache.putAddedResource(newResource);
    return newResource;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R update(R actual, R target, P primary, Context context) {
    log.debug("Updating target resource with type: {}, with id: {}", target.getClass(),
        ResourceID.fromResource(target));
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    R updatedResource =
        client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
            .replace(target);
    temporalResourceCache.putUpdatedResource(updatedResource,
        actual.getMetadata().getResourceVersion());
    return updatedResource;
  }



  @Override
  public Optional<EventSource> eventSource(EventSourceContext<P> context) {
    initResourceMatcherIfNotSet(context.getConfigurationService());
    if (informerEventSource == null) {
      configureWith(context.getConfigurationService(), null, null,
          KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT);
      log.warn("Using default configuration for " + resourceType().getSimpleName()
          + " KubernetesDependentResource, call configureWith to provide configuration");
    }
    return Optional.of(informerEventSource);
  }

  @Override
  public void delete(P primary, Context context) {
    if (!addOwnerReference) {
      var resource = getResource(primary);
      resource.ifPresent(r -> client.resource(r).delete());
    }
  }

  @SuppressWarnings("unchecked")
  protected Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromExtendedClass(getClass());
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    var associatedSecondaryResourceIdentifier =
        informerEventSource.getConfiguration().getAssociatedResourceIdentifier();
    var resourceId =
        associatedSecondaryResourceIdentifier.associatedSecondaryID(primaryResource);
    var tempCacheResource = temporalResourceCache.getResourceFromCache(resourceId);
    if (tempCacheResource.isPresent()) {
      return tempCacheResource;
    } else {
      return informerEventSource.get(resourceId);
    }
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
