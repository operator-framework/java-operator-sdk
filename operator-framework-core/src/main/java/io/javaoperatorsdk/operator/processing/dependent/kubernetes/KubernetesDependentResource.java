package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.AbstractEventSourceHolderDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractEventSourceHolderDependentResource<R, P, InformerEventSource<R, P>>
    implements KubernetesClientAware,
    DependentResourceConfigurator<KubernetesDependentResourceConfig> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected KubernetesClient client;
  private final Matcher<R, P> matcher;
  private final ResourceUpdatePreProcessor<R> processor;
  private final Class<R> resourceType;

  @SuppressWarnings("unchecked")
  public KubernetesDependentResource(Class<R> resourceType) {
    this.resourceType = resourceType;
    matcher = this instanceof Matcher ? (Matcher<R, P>) this
        : GenericKubernetesResourceMatcher.matcherFor(resourceType, this);

    processor = this instanceof ResourceUpdatePreProcessor
        ? (ResourceUpdatePreProcessor<R>) this
        : GenericResourceUpdatePreProcessor.processorFor(resourceType);
  }

  @Override
  public void configureWith(KubernetesDependentResourceConfig config) {
    configureWith(config.labelSelector(), Set.of(config.namespaces()));
  }

  @SuppressWarnings("unchecked")
  private void configureWith(String labelSelector, Set<String> namespaces) {
    final var primaryResourcesRetriever =
        (this instanceof SecondaryToPrimaryMapper) ? (SecondaryToPrimaryMapper<R>) this
            : Mappers.fromOwnerReference();
    final PrimaryToSecondaryMapper<P> secondaryResourceIdentifier =
        (this instanceof PrimaryToSecondaryMapper)
            ? (PrimaryToSecondaryMapper<P>) this
            : ResourceID::fromResource;
    InformerConfiguration<R, P> ic =
        InformerConfiguration.from(resourceType())
            .withLabelSelector(labelSelector)
            .withNamespaces(namespaces)
            .withPrimaryResourcesRetriever(primaryResourcesRetriever)
            .withAssociatedSecondaryResourceIdentifier(secondaryResourceIdentifier)
            .build();
    configureWith(new InformerEventSource<>(ic, client));
  }

  /**
   * Use to share informers between event more resources.
   *
   * @param informerEventSource informer to use*
   */
  public void configureWith(InformerEventSource<R, P> informerEventSource) {
    setEventSource(informerEventSource);
  }


  protected R handleCreate(R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R created = null;
    try {
      prepareEventFiltering(desired, resourceID);
      created = super.handleCreate(desired, primary, context);
      return created;
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(resourceID);
      throw e;
    }
  }

  protected R handleUpdate(R actual, R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R updated = null;
    try {
      prepareEventFiltering(desired, resourceID);
      updated = super.handleUpdate(actual, desired, primary, context);
      return updated;
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(resourceID);
      throw e;
    }
  }

  @SuppressWarnings("unused")
  public R create(R target, P primary, Context<P> context) {
    return prepare(target, primary, "Creating").create(target);
  }

  public R update(R actual, R target, P primary, Context<P> context) {
    var updatedActual = processor.replaceSpecOnActual(actual, target, context);
    return prepare(target, primary, "Updating").createOrReplace(updatedActual);
  }

  public Result<R> match(R actualResource, P primary, Context<P> context) {
    return matcher.match(actualResource, primary, context);
  }

  public void delete(P primary, Context<P> context) {
    if (!addOwnerReference()) {
      var resource = getSecondaryResource(primary);
      resource.ifPresent(r -> client.resource(r).delete());
    }
  }

  @SuppressWarnings("unchecked")
  protected NonNamespaceOperation<R, KubernetesResourceList<R>, Resource<R>> prepare(R desired,
      P primary, String actionName) {
    log.debug("{} target resource with type: {}, with id: {}",
        actionName,
        desired.getClass(),
        ResourceID.fromResource(desired));
    if (addOwnerReference()) {
      desired.addOwnerReference(primary);
    }
    Class<R> targetClass = (Class<R>) desired.getClass();
    return client.resources(targetClass).inNamespace(desired.getMetadata().getNamespace());
  }

  @Override
  protected InformerEventSource<R, P> createEventSource(EventSourceContext<P> context) {
    configureWith(null, context.getControllerConfiguration().getNamespaces());
    log.warn("Using default configuration for " + resourceType().getSimpleName()
        + " KubernetesDependentResource, call configureWith to provide configuration");
    return eventSource();
  }

  protected boolean addOwnerReference() {
    return creatable && !deletable;
  }

  @Override
  public Class<R> resourceType() {
    return resourceType;
  }

  @Override
  public Optional<R> getSecondaryResource(P primaryResource) {
    return eventSource().getSecondaryResource(primaryResource);
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  protected R desired(P primary, Context<P> context) {
    return super.desired(primary, context);
  }

  private void prepareEventFiltering(R desired, ResourceID resourceID) {
    eventSource().prepareForCreateOrUpdateEventFiltering(resourceID, desired);
  }

  private void cleanupAfterEventFiltering(ResourceID resourceID) {
    eventSource().cleanupOnCreateOrUpdateEventFiltering(resourceID);
  }

}
