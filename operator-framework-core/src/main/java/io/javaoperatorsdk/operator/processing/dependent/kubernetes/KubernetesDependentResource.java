package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.Configured;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.dependent.AbstractEventSourceHolderDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher.GenericResourceUpdaterMatcher;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@Ignore
@Configured(by = KubernetesDependent.class, with = KubernetesDependentResourceConfig.class,
    converter = KubernetesDependentConverter.class)
public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractEventSourceHolderDependentResource<R, P, InformerEventSource<R, P>>
    implements DependentResourceConfigurator<KubernetesDependentResourceConfig<R>> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);
  private final boolean garbageCollected = this instanceof GarbageCollected;
  @SuppressWarnings("unchecked")
  private final ResourceUpdaterMatcher<R> updaterMatcher = this instanceof ResourceUpdaterMatcher ? (ResourceUpdaterMatcher<R>) this : GenericResourceUpdaterMatcher.updaterMatcherFor(resourceType());
  private final boolean clustered;
  private KubernetesDependentResourceConfig<R> kubernetesDependentResourceConfig;
  private volatile Boolean useSSA;

  public KubernetesDependentResource(Class<R> resourceType) {
    this(resourceType, null);
  }

  public KubernetesDependentResource(Class<R> resourceType, String name) {
    super(resourceType, name);
    final var primaryResourceType = getPrimaryResourceType();
    clustered = !Namespaced.class.isAssignableFrom(primaryResourceType);
  }

  protected KubernetesDependentResource(Class<R> resourceType, String name,
      boolean primaryIsClustered) {
    super(resourceType, name);
    clustered = primaryIsClustered;
  }

  @SuppressWarnings("unchecked")
  protected Class<P> getPrimaryResourceType() {
    return (Class<P>) Utils.getTypeArgumentFromExtendedClassByIndex(getClass(), 1);
  }

  @Override
  public void configureWith(KubernetesDependentResourceConfig<R> config) {
    this.kubernetesDependentResourceConfig = config;
  }

  private void configureWith(String labelSelector, Set<String> namespaces,
      boolean inheritNamespacesOnChange, EventSourceContext<P> context) {

    if (namespaces.equals(Constants.SAME_AS_CONTROLLER_NAMESPACES_SET)) {
      namespaces = context.getControllerConfiguration().getNamespaces();
    }

    var ic = informerConfigurationBuilder()
        .withLabelSelector(labelSelector)
        .withSecondaryToPrimaryMapper(getSecondaryToPrimaryMapper())
        .withNamespaces(namespaces, inheritNamespacesOnChange)
        .build();

    configureWith(new InformerEventSource<>(name(), ic, context));
  }

  // just to seamlessly handle GenericKubernetesDependentResource
  protected InformerConfiguration.InformerConfigurationBuilder<R> informerConfigurationBuilder() {
    return InformerConfiguration.from(resourceType(), getPrimaryResourceType());
  }

  @SuppressWarnings("unchecked")
  private SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
    if (this instanceof SecondaryToPrimaryMapper) {
      return (SecondaryToPrimaryMapper<R>) this;
    } else if (garbageCollected) {
      return Mappers.fromOwnerReferences(getPrimaryResourceType(), clustered);
    } else if (useNonOwnerRefBasedSecondaryToPrimaryMapping()) {
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

  @SuppressWarnings("unused")
  public R create(R desired, P primary, Context<P> context) {
    if (useSSA(context)) {
      // setting resource version for SSA so only created if it doesn't exist already
      var createIfNotExisting = kubernetesDependentResourceConfig == null
          ? KubernetesDependentResourceConfig.DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA
          : kubernetesDependentResourceConfig.createResourceOnlyIfNotExistingWithSSA();
      if (createIfNotExisting) {
        desired.getMetadata().setResourceVersion("1");
      }
    }
    addMetadata(false, null, desired, primary, context);
    final var resource = prepare(context, desired, primary, "Creating");
    return useSSA(context)
        ? resource
            .fieldManager(context.getControllerConfiguration().fieldManager())
            .forceConflicts()
            .serverSideApply()
        : resource.create();
  }

  public R update(R actual, R desired, P primary, Context<P> context) {
    if (log.isDebugEnabled()) {
      log.debug("Updating actual resource: {} version: {}", ResourceID.fromResource(actual),
          actual.getMetadata().getResourceVersion());
    }
    R updatedResource;
    addMetadata(false, actual, desired, primary, context);
    if (useSSA(context)) {
      updatedResource = prepare(context, desired, primary, "Updating")
          .fieldManager(context.getControllerConfiguration().fieldManager())
          .forceConflicts().serverSideApply();
    } else {
      var updatedActual = updaterMatcher.updateResource(actual, desired, context);
      updatedResource = prepare(context, updatedActual, primary, "Updating").update();
    }
    log.debug("Resource version after update: {}",
        updatedResource.getMetadata().getResourceVersion());
    return updatedResource;
  }

  @Override
  public Result<R> match(R actualResource, P primary, Context<P> context) {
    final var desired = desired(primary, context);
    return match(actualResource, desired, primary, updaterMatcher, context);
  }

  @SuppressWarnings({"unused", "unchecked"})
  public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    return match(actualResource, desired, primary,
        (ResourceUpdaterMatcher<R>) GenericResourceUpdaterMatcher
            .updaterMatcherFor(actualResource.getClass()),
        context);
  }

  public Result<R> match(R actualResource, R desired, P primary, ResourceUpdaterMatcher<R> matcher,
      Context<P> context) {
    final boolean matches;
    addMetadata(true, actualResource, desired, primary, context);
    if (useSSA(context)) {
      matches = SSABasedGenericKubernetesResourceMatcher.getInstance()
          .matches(actualResource, desired, context);
    } else {
      matches = matcher.matches(actualResource, desired, context);
    }
    return Result.computed(matches, desired);
  }

  protected void addMetadata(boolean forMatch, R actualResource, final R target, P primary,
      Context<P> context) {
    if (forMatch) { // keep the current previous annotation
      String actual = actualResource.getMetadata().getAnnotations()
          .get(InformerEventSource.PREVIOUS_ANNOTATION_KEY);
      Map<String, String> annotations = target.getMetadata().getAnnotations();
      if (actual != null) {
        annotations.put(InformerEventSource.PREVIOUS_ANNOTATION_KEY, actual);
      } else {
        annotations.remove(InformerEventSource.PREVIOUS_ANNOTATION_KEY);
      }
    } else if (usePreviousAnnotation(context)) { // set a new one
      eventSource().orElseThrow().addPreviousAnnotation(
          Optional.ofNullable(actualResource).map(r -> r.getMetadata().getResourceVersion())
              .orElse(null),
          target);
    }
    addReferenceHandlingMetadata(target, primary);
  }

  protected boolean useSSA(Context<P> context) {
    if (useSSA == null) {
      useSSA = context.getControllerConfiguration().getConfigurationService()
          .shouldUseSSA(getClass(), resourceType(), configuration().orElse(null));
    }
    return useSSA;
  }

  private boolean usePreviousAnnotation(Context<P> context) {
    return context.getControllerConfiguration().getConfigurationService()
        .previousAnnotationForDependentResourcesEventFiltering();
  }

  @Override
  protected void handleDelete(P primary, R secondary, Context<P> context) {
    if (secondary != null) {
      context.getClient().resource(secondary).delete();
    }
  }

  @SuppressWarnings("unused")
  public void deleteTargetResource(P primary, R resource, String key, Context<P> context) {
    context.getClient().resource(resource).delete();
  }

  @SuppressWarnings("unused")
  protected Resource<R> prepare(Context<P> context, R desired, P primary, String actionName) {
    log.debug("{} target resource with type: {}, with id: {}",
        actionName,
        desired.getClass(),
        ResourceID.fromResource(desired));

    return context.getClient().resource(desired);
  }

  protected void addReferenceHandlingMetadata(R desired, P primary) {
    if (addOwnerReference()) {
      ReconcilerUtils.checkIfCanAddOwnerReference(primary, desired);
      desired.addOwnerReference(primary);
    } else if (useNonOwnerRefBasedSecondaryToPrimaryMapping()) {
      addSecondaryToPrimaryMapperAnnotations(desired, primary);
    }
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
    return eventSource().orElseThrow();
  }

  private boolean useNonOwnerRefBasedSecondaryToPrimaryMapping() {
    return !garbageCollected && isCreatable();
  }

  protected void addSecondaryToPrimaryMapperAnnotations(R desired, P primary) {
    addSecondaryToPrimaryMapperAnnotations(desired, primary, Mappers.DEFAULT_ANNOTATION_FOR_NAME,
        Mappers.DEFAULT_ANNOTATION_FOR_NAMESPACE);
  }

  protected void addSecondaryToPrimaryMapperAnnotations(R desired, P primary, String nameKey,
      String namespaceKey) {
    var annotations = desired.getMetadata().getAnnotations();
    annotations.put(nameKey, primary.getMetadata().getName());
    var primaryNamespaces = primary.getMetadata().getNamespace();
    if (primaryNamespaces != null) {
      annotations.put(namespaceKey, primary.getMetadata().getNamespace());
    }
  }

  @Override
  protected Optional<R> selectManagedSecondaryResource(Set<R> secondaryResources, P primary,
      Context<P> context) {
    ResourceID managedResourceID = managedSecondaryResourceID(primary, context);
    return secondaryResources.stream()
        .filter(r -> r.getMetadata().getName().equals(managedResourceID.getName()) &&
            Objects.equals(r.getMetadata().getNamespace(),
                managedResourceID.getNamespace().orElse(null)))
        .findFirst();
  }

  /**
   * Override this method in order to optimize and not compute the desired when selecting the target
   * secondary resource. Simply, a static ResourceID can be returned.
   *
   * @param primary resource
   * @param context of current reconciliation
   * @return id of the target managed resource
   */
  protected ResourceID managedSecondaryResourceID(P primary, Context<P> context) {
    return ResourceID.fromResource(desired(primary, context));
  }

  protected boolean addOwnerReference() {
    return garbageCollected;
  }

  @Override
  protected R desired(P primary, Context<P> context) {
    return super.desired(primary, context);
  }

  @Override
  public Optional<KubernetesDependentResourceConfig<R>> configuration() {
    return Optional.ofNullable(kubernetesDependentResourceConfig);
  }

  @Override
  public boolean isDeletable() {
    return super.isDeletable() && !garbageCollected;
  }

}
