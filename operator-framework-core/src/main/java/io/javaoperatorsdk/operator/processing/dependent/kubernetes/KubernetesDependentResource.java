package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.HashMap;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.AbstractEventSourceHolderDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@Ignore
@SuppressWarnings("rawtypes")
public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractEventSourceHolderDependentResource<R, P, InformerEventSource<R, P>>
    implements KubernetesClientAware,
    DependentResourceConfigurator<KubernetesDependentResourceConfig<R>> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected KubernetesClient client;
  private final Matcher<R, P> matcher;
  private final ResourceUpdatePreProcessor<R> processor;
  private final boolean garbageCollected = this instanceof GarbageCollected;
  private KubernetesDependentResourceConfig kubernetesDependentResourceConfig;

  @SuppressWarnings("unchecked")
  public KubernetesDependentResource(Class<R> resourceType) {
    super(resourceType);
    matcher = this instanceof Matcher ? (Matcher<R, P>) this
        : GenericKubernetesResourceMatcher.matcherFor(resourceType, this);

    processor = this instanceof ResourceUpdatePreProcessor
        ? (ResourceUpdatePreProcessor<R>) this
        : GenericResourceUpdatePreProcessor.processorFor(resourceType);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void configureWith(KubernetesDependentResourceConfig<R> config) {
    this.kubernetesDependentResourceConfig = config;
    var discriminator = kubernetesDependentResourceConfig.getResourceDiscriminator();
    if (discriminator != null) {
      setResourceDiscriminator(discriminator);
    }
    config.getEventSourceToUse().ifPresent(n -> {
      doNotProvideEventSource();
      setEventSourceToUse(n);
    });
  }

  private void configureWith(String labelSelector, Set<String> namespaces,
      boolean inheritNamespacesOnChange, EventSourceContext<P> context) {

    if (namespaces.equals(Constants.SAME_AS_CONTROLLER_NAMESPACES_SET)) {
      namespaces = context.getControllerConfiguration().getNamespaces();
    }

    var ic = InformerConfiguration.from(resourceType())
        .withLabelSelector(labelSelector)
        .withSecondaryToPrimaryMapper(getSecondaryToPrimaryMapper())
        .withNamespaces(namespaces, inheritNamespacesOnChange)
        .build();

    configureWith(new InformerEventSource<>(ic, context));

  }

  @SuppressWarnings("unchecked")
  private SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
    if (this instanceof SecondaryToPrimaryMapper) {
      return (SecondaryToPrimaryMapper<R>) this;
    } else if (garbageCollected) {
      return Mappers.fromOwnerReference();
    } else if (useDefaultAnnotationsToIdentifyPrimary()) {
      return Mappers.fromDefaultAnnotations();
    } else {
      throw new OperatorException("Provide a SecondaryToPrimaryMapper to associate " +
          "this resource with the primary resource. DependentResource: " + getClass().getName());
    }
  }

  /**
   * Use to share informers between event more resources.
   *
   * @param informerEventSource informer to use
   */
  public void configureWith(InformerEventSource<R, P> informerEventSource) {
    setEventSource(informerEventSource);
  }


  protected R handleCreate(R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(desired);
    try {
      prepareEventFiltering(desired, resourceID);
      return super.handleCreate(desired, primary, context);
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(resourceID);
      throw e;
    }
  }

  protected R handleUpdate(R actual, R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(desired);
    try {
      prepareEventFiltering(desired, resourceID);
      return super.handleUpdate(actual, desired, primary, context);
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(resourceID);
      throw e;
    }
  }

  @SuppressWarnings("unused")
  public R create(R target, P primary, Context<P> context) {
    return prepare(target, primary, "Creating").create();
  }

  public R update(R actual, R target, P primary, Context<P> context) {
    var updatedActual = processor.replaceSpecOnActual(actual, target, context);
    return prepare(updatedActual, primary, "Updating").replace();
  }

  public Result<R> match(R actualResource, P primary, Context<P> context) {
    return matcher.match(actualResource, primary, context);
  }

  public Result<R> match(R actualResource, P primary, int index, Context<P> context) {
    return matcher.match(actualResource, primary, index, context);
  }

  public void delete(P primary, Context<P> context) {
    if (bulk) {
      deleteBulkResourcesIfRequired(0, lastKnownBulkSize(), primary, context);
    } else {
      var resource = getSecondaryResource(primary, context);
      resource.ifPresent(r -> client.resource(r).delete());
    }
  }

  public void deleteBulkResourceWithIndex(P primary, R resource, int i, Context<P> context) {
    client.resource(resource).delete();
  }

  @SuppressWarnings("unchecked")
  protected Resource<R> prepare(R desired,
      P primary, String actionName) {
    log.debug("{} target resource with type: {}, with id: {}",
        actionName,
        desired.getClass(),
        ResourceID.fromResource(desired));
    if (addOwnerReference()) {
      desired.addOwnerReference(primary);
    } else if (useDefaultAnnotationsToIdentifyPrimary()) {
      addDefaultSecondaryToPrimaryMapperAnnotations(desired, primary);
    }
    return client.resource(desired).inNamespace(desired.getMetadata().getNamespace());
  }

  @Override
  @SuppressWarnings("unchecked")
  protected InformerEventSource<R, P> createEventSource(EventSourceContext<P> context) {
    if (kubernetesDependentResourceConfig != null) {
      // sets the filters for the dependent resource, which are applied by parent class

      onAddFilter = kubernetesDependentResourceConfig.onAddFilter();
      onUpdateFilter = kubernetesDependentResourceConfig.onUpdateFilter();
      onDeleteFilter = kubernetesDependentResourceConfig.onDeleteFilter();
      genericFilter = kubernetesDependentResourceConfig.genericFilter();
      var discriminator = kubernetesDependentResourceConfig.getResourceDiscriminator();
      if (discriminator != null) {
        setResourceDiscriminator(discriminator);
      }
      configureWith(kubernetesDependentResourceConfig.labelSelector(),
          kubernetesDependentResourceConfig.namespaces(),
          !kubernetesDependentResourceConfig.wereNamespacesConfigured(), context);
    } else {
      configureWith(null, context.getControllerConfiguration().getNamespaces(),
          true, context);
      log.warn(
          "Using default configuration for {} KubernetesDependentResource, call configureWith to provide configuration",
          resourceType().getSimpleName());
    }
    return (InformerEventSource<R, P>) eventSource().orElseThrow();
  }

  private boolean useDefaultAnnotationsToIdentifyPrimary() {
    return !(this instanceof SecondaryToPrimaryMapper) && !garbageCollected && creatable;
  }

  private void addDefaultSecondaryToPrimaryMapperAnnotations(R desired, P primary) {
    var annotations = desired.getMetadata().getAnnotations();
    if (annotations == null) {
      annotations = new HashMap<>();
      desired.getMetadata().setAnnotations(annotations);
    }
    annotations.put(Mappers.DEFAULT_ANNOTATION_FOR_NAME, primary.getMetadata().getName());
    var primaryNamespaces = primary.getMetadata().getNamespace();
    if (primaryNamespaces != null) {
      annotations.put(
          Mappers.DEFAULT_ANNOTATION_FOR_NAMESPACE, primary.getMetadata().getNamespace());
    }
  }

  protected boolean addOwnerReference() {
    return garbageCollected;
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

  @Override
  protected R desired(P primary, int index, Context<P> context) {
    return super.desired(primary, index, context);
  }

  private void prepareEventFiltering(R desired, ResourceID resourceID) {
    ((InformerEventSource<R, P>) eventSource().orElseThrow())
        .prepareForCreateOrUpdateEventFiltering(resourceID, desired);
  }

  private void cleanupAfterEventFiltering(ResourceID resourceID) {
    ((InformerEventSource<R, P>) eventSource().orElseThrow())
        .cleanupOnCreateOrUpdateEventFiltering(resourceID);
  }
}
