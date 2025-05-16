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
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.AbstractEventSourceHolderDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@Ignore
@Configured(
    by = KubernetesDependent.class,
    with = KubernetesDependentResourceConfig.class,
    converter = KubernetesDependentConverter.class)
public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractEventSourceHolderDependentResource<R, P, InformerEventSource<R, P>>
    implements ConfiguredDependentResource<KubernetesDependentResourceConfig<R>> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  private final boolean garbageCollected = this instanceof GarbageCollected;
  private KubernetesDependentResourceConfig<R> kubernetesDependentResourceConfig;
  private volatile Boolean useSSA;
  private volatile Boolean usePreviousAnnotationForEventFiltering;

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
      var createIfNotExisting =
          kubernetesDependentResourceConfig == null
              ? KubernetesDependentResourceConfig
                  .DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA
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
      log.debug(
          "Updating actual resource: {} version: {}",
          ResourceID.fromResource(actual),
          actual.getMetadata().getResourceVersion());
    }
    R updatedResource;
    addMetadata(false, actual, desired, primary, context);
    if (useSSA(context)) {
      updatedResource =
          prepare(context, desired, primary, "Updating")
              .fieldManager(context.getControllerConfiguration().fieldManager())
              .forceConflicts()
              .serverSideApply();
    } else {
      var updatedActual = GenericResourceUpdater.updateResource(actual, desired, context);
      updatedResource = prepare(context, updatedActual, primary, "Updating").update();
    }
    log.debug(
        "Resource version after update: {}", updatedResource.getMetadata().getResourceVersion());
    return updatedResource;
  }

  @Override
  public Result<R> match(R actualResource, P primary, Context<P> context) {
    final var desired = desired(primary, context);
    return match(actualResource, desired, primary, context);
  }

  public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    final boolean matches;
    addMetadata(true, actualResource, desired, primary, context);
    if (useSSA(context)) {
      matches =
          configuration()
              .map(KubernetesDependentResourceConfig::matcher)
              .orElse(SSABasedGenericKubernetesResourceMatcher.getInstance())
              .matches(actualResource, desired, context);
    } else {
      matches =
          GenericKubernetesResourceMatcher.match(desired, actualResource, false, false, context)
              .matched();
    }
    return Result.computed(matches, desired);
  }

  protected void addMetadata(
      boolean forMatch, R actualResource, final R target, P primary, Context<P> context) {
    if (forMatch) { // keep the current previous annotation
      String actual =
          actualResource
              .getMetadata()
              .getAnnotations()
              .get(InformerEventSource.PREVIOUS_ANNOTATION_KEY);
      Map<String, String> annotations = target.getMetadata().getAnnotations();
      if (actual != null) {
        annotations.put(InformerEventSource.PREVIOUS_ANNOTATION_KEY, actual);
      } else {
        annotations.remove(InformerEventSource.PREVIOUS_ANNOTATION_KEY);
      }
    } else if (usePreviousAnnotation(context)) { // set a new one
      eventSource()
          .orElseThrow()
          .addPreviousAnnotation(
              Optional.ofNullable(actualResource)
                  .map(r -> r.getMetadata().getResourceVersion())
                  .orElse(null),
              target);
    }
    addReferenceHandlingMetadata(target, primary);
  }

  protected boolean useSSA(Context<P> context) {
    if (useSSA == null) {
      useSSA =
          context
              .getControllerConfiguration()
              .getConfigurationService()
              .shouldUseSSA(getClass(), resourceType(), configuration().orElse(null));
    }
    return useSSA;
  }

  private boolean usePreviousAnnotation(Context<P> context) {
    if (usePreviousAnnotationForEventFiltering == null) {
      usePreviousAnnotationForEventFiltering =
          context
                  .getControllerConfiguration()
                  .getConfigurationService()
                  .previousAnnotationForDependentResourcesEventFiltering()
              && !context
                  .getControllerConfiguration()
                  .getConfigurationService()
                  .withPreviousAnnotationForDependentResourcesBlocklist()
                  .contains(this.resourceType());
    }
    return usePreviousAnnotationForEventFiltering;
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
    log.debug(
        "{} target resource with type: {}, with id: {}",
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
    final InformerEventSourceConfiguration.Builder<R> configBuilder =
        informerConfigurationBuilder(context)
            .withSecondaryToPrimaryMapper(getSecondaryToPrimaryMapper(context).orElseThrow())
            .withName(name());

    // update configuration from annotation if specified
    if (kubernetesDependentResourceConfig != null
        && kubernetesDependentResourceConfig.informerConfig() != null) {
      configBuilder.updateFrom(kubernetesDependentResourceConfig.informerConfig());
    }

    var es = new InformerEventSource<>(configBuilder.build(), context);
    setEventSource(es);
    return eventSource().orElseThrow();
  }

  /**
   * To handle {@link io.fabric8.kubernetes.api.model.GenericKubernetesResource} based dependents.
   */
  protected InformerEventSourceConfiguration.Builder<R> informerConfigurationBuilder(
      EventSourceContext<P> context) {
    return InformerEventSourceConfiguration.from(resourceType(), context.getPrimaryResourceClass());
  }

  private boolean useNonOwnerRefBasedSecondaryToPrimaryMapping() {
    return !garbageCollected && isCreatable();
  }

  protected void addSecondaryToPrimaryMapperAnnotations(R desired, P primary) {
    addSecondaryToPrimaryMapperAnnotations(
        desired,
        primary,
        Mappers.DEFAULT_ANNOTATION_FOR_NAME,
        Mappers.DEFAULT_ANNOTATION_FOR_NAMESPACE,
        Mappers.DEFAULT_ANNOTATION_FOR_PRIMARY_TYPE);
  }

  protected void addSecondaryToPrimaryMapperAnnotations(
      R desired, P primary, String nameKey, String namespaceKey, String typeKey) {
    var annotations = desired.getMetadata().getAnnotations();
    annotations.put(nameKey, primary.getMetadata().getName());
    var primaryNamespaces = primary.getMetadata().getNamespace();
    if (primaryNamespaces != null) {
      annotations.put(namespaceKey, primary.getMetadata().getNamespace());
    }
    annotations.put(typeKey, GroupVersionKind.gvkFor(primary.getClass()).toGVKString());
  }

  @Override
  protected Optional<R> selectTargetSecondaryResource(
      Set<R> secondaryResources, P primary, Context<P> context) {
    ResourceID managedResourceID = targetSecondaryResourceID(primary, context);
    return secondaryResources.stream()
        .filter(
            r ->
                r.getMetadata().getName().equals(managedResourceID.getName())
                    && Objects.equals(
                        r.getMetadata().getNamespace(),
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
  protected ResourceID targetSecondaryResourceID(P primary, Context<P> context) {
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
        return Optional.of(
            Mappers.fromOwnerReferences(context.getPrimaryResourceClass(), clustered));
      } else if (isCreatable()) {
        return Optional.of(Mappers.fromDefaultAnnotations(context.getPrimaryResourceClass()));
      }
    }
    return Optional.empty();
  }
}
