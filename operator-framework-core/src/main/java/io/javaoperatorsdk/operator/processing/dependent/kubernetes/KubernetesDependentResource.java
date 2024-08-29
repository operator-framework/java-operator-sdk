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
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.Configured;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
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
    implements ConfiguredDependentResource<KubernetesDependentResourceConfig<R>> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);
  private final boolean garbageCollected = this instanceof GarbageCollected;
  @SuppressWarnings("unchecked")
  private final ResourceUpdaterMatcher<R> updaterMatcher = this instanceof ResourceUpdaterMatcher ? (ResourceUpdaterMatcher<R>) this : GenericResourceUpdaterMatcher.updaterMatcherFor(resourceType());
  private KubernetesDependentResourceConfig<R> kubernetesDependentResourceConfig;
  private volatile Boolean useSSA;

  public KubernetesDependentResource(Class<R> resourceType) {
    this(resourceType, null);
  }

  public KubernetesDependentResource(Class<R> resourceType, String name) {
    super(resourceType, name);
  }

  @Override
  public void configureWith(KubernetesDependentResourceConfig<R> config) {
    this.kubernetesDependentResourceConfig = config;
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

  @SuppressWarnings({"unused"})
  public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    return match(actualResource, desired, primary,
        GenericResourceUpdaterMatcher.updaterMatcherFor(),
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
      useSSA = context.getControllerConfiguration().getConfigurationService().shouldUseSSA(this);
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
  protected InformerEventSource<R, P> createEventSource(EventSourceContext<P> context) {
    final InformerConfiguration.InformerConfigurationBuilder<R> configBuilder =
        informerConfigurationBuilder(context)
            .withSecondaryToPrimaryMapper(getSecondaryToPrimaryMapper(context).orElseThrow())
            .withName(name());

    // update configuration from annotation if specified
    if (kubernetesDependentResourceConfig != null
        && kubernetesDependentResourceConfig.informerConfig() != null) {
      kubernetesDependentResourceConfig.informerConfig().updateInformerConfigBuilder(configBuilder);
    }

    var es = new InformerEventSource<>(configBuilder.build(), context);
    setEventSource(es);
    return eventSource().orElseThrow();
  }

  /**
   * To handle {@link io.fabric8.kubernetes.api.model.GenericKubernetesResource} based dependents.
   */
  protected InformerConfiguration.InformerConfigurationBuilder<R> informerConfigurationBuilder(
      EventSourceContext<P> context) {
    return InformerConfiguration.from(resourceType(), context.getPrimaryResourceClass());
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

  @SuppressWarnings("unchecked")
  protected Optional<SecondaryToPrimaryMapper<R>> getSecondaryToPrimaryMapper(
      EventSourceContext<P> context) {
    if (this instanceof SecondaryToPrimaryMapper<?>) {
      return Optional.of((SecondaryToPrimaryMapper<R>) this);
    } else {
      var clustered = !Namespaced.class.isAssignableFrom(context.getPrimaryResourceClass());
      if (garbageCollected) {
        return Optional
            .of(Mappers.fromOwnerReferences(context.getPrimaryResourceClass(), clustered));
      } else if (isCreatable()) {
        return Optional.of(Mappers.fromDefaultAnnotations());
      }
    }
    return Optional.empty();
  }
}
