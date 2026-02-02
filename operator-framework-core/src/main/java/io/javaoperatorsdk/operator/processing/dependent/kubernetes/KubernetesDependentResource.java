/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
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

  public KubernetesDependentResource() {}

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
    var ssa = useSSA(context);
    if (ssa) {
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
    log.debug(
        "Creating target resource with type: {}, with id: {} use ssa: {}",
        desired.getClass(),
        ResourceID.fromResource(desired),
        ssa);

    return ssa
        ? context.resourceOperations().serverSideApply(desired)
        : context.resourceOperations().create(desired);
  }

  public R update(R actual, R desired, P primary, Context<P> context) {
    boolean ssa = useSSA(context);
    if (log.isDebugEnabled()) {
      log.debug(
          "Updating actual resource: {} version: {}; SSA: {}",
          ResourceID.fromResource(actual),
          actual.getMetadata().getResourceVersion(),
          ssa);
    }
    R updatedResource;
    addMetadata(false, actual, desired, primary, context);
    log.debug(
        "Updating target resource with type: {}, with id: {} use ssa: {}",
        desired.getClass(),
        ResourceID.fromResource(desired),
        ssa);
    if (ssa) {
      updatedResource = context.resourceOperations().serverSideApply(desired);
    } else {
      var updatedActual = GenericResourceUpdater.updateResource(actual, desired, context);
      updatedResource = context.resourceOperations().update(updatedActual);
    }
    log.debug(
        "Resource version after update: {}", updatedResource.getMetadata().getResourceVersion());
    return updatedResource;
  }

  @Override
  public Result<R> match(R actualResource, P primary, Context<P> context) {
    final var desired = getOrComputeDesired(context);
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

  @Override
  protected void handleDelete(P primary, R secondary, Context<P> context) {
    if (secondary != null) {
      context.getClient().resource(secondary).delete();
    }
  }

  @SuppressWarnings("unused")
  public void deleteTargetResource(P primary, R resource, ResourceID key, Context<P> context) {
    context.getClient().resource(resource).delete();
  }

  protected void addReferenceHandlingMetadata(R desired, P primary) {
    if (addOwnerReference()) {
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
    return ResourceID.fromResource(getOrComputeDesired(context));
  }

  protected boolean addOwnerReference() {
    return garbageCollected;
  }

  @Override
  protected R getOrComputeDesired(Context<P> context) {
    return super.getOrComputeDesired(context);
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
