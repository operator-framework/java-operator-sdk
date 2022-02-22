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
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Creator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Matcher;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Updater;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractDependentResource<R, P>
    implements KubernetesClientAware, EventSourceProvider<P>,
    DependentResourceConfigurator<KubernetesDependentResourceConfig>,
    Matcher<R> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected KubernetesClient client;
  private InformerEventSource<R, P> informerEventSource;
  private boolean addOwnerReference;
  protected ResourceUpdatePreProcessor<R> resourceUpdatePreProcessor;

  public KubernetesDependentResource() {
    init(new CreateDependentOperation(), new UpdateDependentOperation(),
        GenericKubernetesResourceMatcher.matcherFor(resourceType()));
  }

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

  private class DependentOperation {
    private final String actionName;

    public DependentOperation(String actionName) {
      this.actionName = actionName;
    }

    @SuppressWarnings("unchecked")
    protected NonNamespaceOperation<R, KubernetesResourceList<R>, Resource<R>> prepare(R desired,
        P primary) {
      log.debug("{} target resource with type: {}, with id: {}",
          actionName,
          desired.getClass(),
          ResourceID.fromResource(desired));
      if (addOwnerReference) {
        desired.addOwnerReference(primary);
      }
      Class<R> targetClass = (Class<R>) desired.getClass();
      return client.resources(targetClass).inNamespace(desired.getMetadata().getNamespace());
    }
  }

  private class CreateDependentOperation extends DependentOperation implements Creator<R, P> {

    public CreateDependentOperation() {
      super("Creating");
    }

    @Override
    public void create(R target, P primary, Context context) {
      prepare(target, primary).create(target);
    }
  }

  private class UpdateDependentOperation extends DependentOperation implements Updater<R, P> {

    public UpdateDependentOperation() {
      super("Updating");
    }

    @Override
    public void update(R actual, R target, P primary, Context context) {
      var updatedActual = resourceUpdatePreProcessor.replaceSpecOnActual(actual, target);
      prepare(target, primary).replace(updatedActual);
    }
  }

  @Override
  public boolean match(R actualResource, R desiredResource, Context context) {
    return matcher.match(actualResource, desiredResource, context);
  }

  @Override
  public EventSource eventSource(EventSourceContext<P> context) {
    initResourceMatcherAndUpdatePreProcessorIfNotSet(context.getConfigurationService());
    if (informerEventSource == null) {
      configureWith(context.getConfigurationService(), null, null,
          KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT);
      log.warn("Using default configuration for " + resourceType().getSimpleName()
          + " KubernetesDependentResource, call configureWith to provide configuration");
    }
    return informerEventSource;
  }

  public KubernetesDependentResource<R, P> setInformerEventSource(
      InformerEventSource<R, P> informerEventSource) {
    this.informerEventSource = informerEventSource;
    return this;
  }
  
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
  protected void initResourceMatcherAndUpdatePreProcessorIfNotSet(
      ConfigurationService configurationService) {
    if (resourceUpdatePreProcessor == null) {
      resourceUpdatePreProcessor =
          new ResourceUpdatePreProcessor<>(configurationService.getResourceCloner());
    }
  }

  public KubernetesDependentResource<R, P> setResourceUpdatePreProcessor(
      ResourceUpdatePreProcessor<R> resourceUpdatePreProcessor) {
    this.resourceUpdatePreProcessor = resourceUpdatePreProcessor;
    return this;
  }
}
