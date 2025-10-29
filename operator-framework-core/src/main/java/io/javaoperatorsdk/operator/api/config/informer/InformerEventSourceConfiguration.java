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
package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.Informable;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.SAME_AS_CONTROLLER_NAMESPACES_SET;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACE_SET;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE_SET;

public interface InformerEventSourceConfiguration<R extends HasMetadata> extends Informable<R> {

  static <R extends HasMetadata> Builder<R> from(
      Class<R> resourceClass, Class<? extends HasMetadata> primaryResourceClass) {
    return new Builder<>(resourceClass, primaryResourceClass);
  }

  static Builder<GenericKubernetesResource> from(
      GroupVersionKind groupVersionKind, Class<? extends HasMetadata> primaryResourceClass) {
    return new Builder<>(groupVersionKind, primaryResourceClass);
  }

  /**
   * Used in case the watched namespaces are changed dynamically, thus when operator is running (See
   * {@link io.javaoperatorsdk.operator.RegisteredController}). If true, changing the target
   * namespaces of a controller would result to change target namespaces for the
   * InformerEventSource.
   *
   * @return if namespace changes should be followed
   */
  default boolean followControllerNamespaceChanges() {
    return getInformerConfig().getFollowControllerNamespaceChanges();
  }

  /**
   * Returns the configured {@link SecondaryToPrimaryMapper} which will allow JOSDK to identify
   * which secondary resources are associated with a given primary resource in cases where there is
   * no explicit reference to the primary resource (e.g. using owner references) in the associated
   * secondary resources.
   *
   * @return the configured {@link SecondaryToPrimaryMapper}
   * @see SecondaryToPrimaryMapper for more explanations on when using such a mapper is useful /
   *     needed
   */
  SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper();

  <P extends HasMetadata> PrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper();

  Optional<GroupVersionKind> getGroupVersionKind();

  default String name() {
    return getInformerConfig().getName();
  }

  /**
   * Optional, specific kubernetes client, typically to connect to a different cluster than the rest
   * of the operator. Note that this is solely for multi cluster support.
   */
  default Optional<KubernetesClient> getKubernetesClient() {
    return Optional.empty();
  }

  class DefaultInformerEventSourceConfiguration<R extends HasMetadata>
      implements InformerEventSourceConfiguration<R> {
    private final PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private final GroupVersionKind groupVersionKind;
    private final InformerConfiguration<R> informerConfig;
    private final KubernetesClient kubernetesClient;

    protected DefaultInformerEventSourceConfiguration(
        GroupVersionKind groupVersionKind,
        PrimaryToSecondaryMapper<?> primaryToSecondaryMapper,
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper,
        InformerConfiguration<R> informerConfig,
        KubernetesClient kubernetesClient) {
      this.informerConfig = Objects.requireNonNull(informerConfig);
      this.groupVersionKind = groupVersionKind;
      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
      this.kubernetesClient = kubernetesClient;
    }

    @Override
    public InformerConfiguration<R> getInformerConfig() {
      return informerConfig;
    }

    @Override
    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P extends HasMetadata> PrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper() {
      return (PrimaryToSecondaryMapper<P>) primaryToSecondaryMapper;
    }

    @Override
    public Optional<GroupVersionKind> getGroupVersionKind() {
      return Optional.ofNullable(groupVersionKind);
    }

    @Override
    public Optional<KubernetesClient> getKubernetesClient() {
      return Optional.ofNullable(kubernetesClient);
    }
  }

  @SuppressWarnings({"unused", "UnusedReturnValue"})
  class Builder<R extends HasMetadata> {

    private final Class<R> resourceClass;
    private final GroupVersionKind groupVersionKind;
    private final Class<? extends HasMetadata> primaryResourceClass;
    private final InformerConfiguration<R>.Builder config;
    private String name;
    private PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private KubernetesClient kubernetesClient;

    private Builder(Class<R> resourceClass, Class<? extends HasMetadata> primaryResourceClass) {
      this(resourceClass, primaryResourceClass, null);
    }

    @SuppressWarnings("unchecked")
    private Builder(
        GroupVersionKind groupVersionKind, Class<? extends HasMetadata> primaryResourceClass) {
      this((Class<R>) GenericKubernetesResource.class, primaryResourceClass, groupVersionKind);
    }

    private Builder(
        Class<R> resourceClass,
        Class<? extends HasMetadata> primaryResourceClass,
        GroupVersionKind groupVersionKind) {
      this.resourceClass = resourceClass;
      this.groupVersionKind = groupVersionKind;
      this.primaryResourceClass = primaryResourceClass;
      this.config = InformerConfiguration.builder(resourceClass);
    }

    public Builder<R> withName(String name) {
      this.name = name;
      config.withName(name);
      return this;
    }

    public <P extends HasMetadata> Builder<R> withPrimaryToSecondaryMapper(
        PrimaryToSecondaryMapper<P> primaryToSecondaryMapper) {
      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      return this;
    }

    public Builder<R> withSecondaryToPrimaryMapper(
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
      this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
      return this;
    }

    /**
     * Use this is case want to create an InformerEventSource that handles resources from different
     * cluster.
     */
    public Builder<R> withKubernetesClient(KubernetesClient kubernetesClient) {
      this.kubernetesClient = kubernetesClient;
      return this;
    }

    public String getName() {
      return name;
    }

    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    public Builder<R> withNamespaces(Set<String> namespaces) {
      config.withNamespaces(namespaces);
      return this;
    }

    /**
     * @since 5.1.1
     */
    public Builder<R> withNamespaces(String... namespaces) {
      config.withNamespaces(Set.of(namespaces));
      return this;
    }

    public Builder<R> withNamespacesInheritedFromController() {
      withNamespaces(SAME_AS_CONTROLLER_NAMESPACES_SET);
      return this;
    }

    public Builder<R> withWatchAllNamespaces() {
      withNamespaces(WATCH_ALL_NAMESPACE_SET);
      return this;
    }

    public Builder<R> withWatchCurrentNamespace() {
      withNamespaces(WATCH_CURRENT_NAMESPACE_SET);
      return this;
    }

    /**
     * Whether the associated informer should track changes made to the parent {@link
     * io.javaoperatorsdk.operator.processing.Controller}'s namespaces configuration.
     *
     * @param followChanges {@code true} to reconfigure the associated informer when the parent
     *     controller's namespaces are reconfigured, {@code false} otherwise
     * @return the builder instance so that calls can be chained fluently
     */
    public Builder<R> withFollowControllerNamespacesChanges(boolean followChanges) {
      config.withFollowControllerNamespacesChanges(followChanges);
      return this;
    }

    public Builder<R> withLabelSelector(String labelSelector) {
      config.withLabelSelector(labelSelector);
      return this;
    }

    public Builder<R> withOnAddFilter(OnAddFilter<? super R> onAddFilter) {
      config.withOnAddFilter(onAddFilter);
      return this;
    }

    public Builder<R> withOnUpdateFilter(OnUpdateFilter<? super R> onUpdateFilter) {
      config.withOnUpdateFilter(onUpdateFilter);
      return this;
    }

    public Builder<R> withOnDeleteFilter(OnDeleteFilter<? super R> onDeleteFilter) {
      config.withOnDeleteFilter(onDeleteFilter);
      return this;
    }

    public Builder<R> withGenericFilter(GenericFilter<? super R> genericFilter) {
      config.withGenericFilter(genericFilter);
      return this;
    }

    public Builder<R> withItemStore(ItemStore<R> itemStore) {
      config.withItemStore(itemStore);
      return this;
    }

    public Builder<R> withInformerListLimit(Long informerListLimit) {
      config.withInformerListLimit(informerListLimit);
      return this;
    }

    public Builder<R> withFieldSelector(FieldSelector fieldSelector) {
      config.withFieldSelector(fieldSelector);
      return this;
    }

    public void updateFrom(InformerConfiguration<R> informerConfig) {
      if (informerConfig != null) {
        final var informerConfigName = informerConfig.getName();
        if (informerConfigName != null) {
          this.name = informerConfigName;
        }
        config
            .withNamespaces(informerConfig.getNamespaces())
            .withFollowControllerNamespacesChanges(
                informerConfig.getFollowControllerNamespaceChanges())
            .withLabelSelector(informerConfig.getLabelSelector())
            .withItemStore(informerConfig.getItemStore())
            .withOnAddFilter(informerConfig.getOnAddFilter())
            .withOnUpdateFilter(informerConfig.getOnUpdateFilter())
            .withOnDeleteFilter(informerConfig.getOnDeleteFilter())
            .withGenericFilter(informerConfig.getGenericFilter())
            .withInformerListLimit(informerConfig.getInformerListLimit())
            .withFieldSelector(informerConfig.getFieldSelector());
      }
    }

    public InformerEventSourceConfiguration<R> build() {
      if (groupVersionKind != null
          && !GenericKubernetesResource.class.isAssignableFrom(resourceClass)) {
        throw new IllegalStateException(
            "If GroupVersionKind is set the resource type must be"
                + " GenericKubernetesDependentResource");
      }

      return new DefaultInformerEventSourceConfiguration<>(
          groupVersionKind,
          primaryToSecondaryMapper,
          Objects.requireNonNullElse(
              secondaryToPrimaryMapper,
              Mappers.fromOwnerReferences(
                  HasMetadata.getApiVersion(primaryResourceClass),
                  HasMetadata.getKind(primaryResourceClass),
                  false)),
          config.build(),
          kubernetesClient);
    }
  }
}
