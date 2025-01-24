package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.Informable;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public interface InformerEventSourceConfiguration<R extends HasMetadata>
    extends Informable<R> {

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
    return getInformerConfig().getFollowControllerNamespacesChanges();
  }

  /**
   * Returns the configured {@link SecondaryToPrimaryMapper} which will allow JOSDK to identify
   * which secondary resources are associated with a given primary resource in cases where there is
   * no explicit reference to the primary resource (e.g. using owner references) in the associated
   * secondary resources.
   *
   * @return the configured {@link SecondaryToPrimaryMapper}
   * @see SecondaryToPrimaryMapper for more explanations on when using such a mapper is useful /
   *      needed
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

    private Builder(Class<R> resourceClass,
        Class<? extends HasMetadata> primaryResourceClass) {
      this(resourceClass, primaryResourceClass, null);
    }

    @SuppressWarnings("unchecked")
    private Builder(GroupVersionKind groupVersionKind,
        Class<? extends HasMetadata> primaryResourceClass) {
      this((Class<R>) GenericKubernetesResource.class, primaryResourceClass, groupVersionKind);
    }

    private Builder(Class<R> resourceClass,
        Class<? extends HasMetadata> primaryResourceClass, GroupVersionKind groupVersionKind) {
      this.resourceClass = resourceClass;
      this.groupVersionKind = groupVersionKind;
      this.primaryResourceClass = primaryResourceClass;
      this.config = InformerConfiguration.builder(resourceClass);
    }

    public Builder<R> withInformerConfiguration(
        Consumer<InformerConfiguration<R>.Builder> configurator) {
      configurator.accept(config);
      return this;
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
    public Builder<R> withKubernetesClient(
        KubernetesClient kubernetesClient) {
      this.kubernetesClient = kubernetesClient;
      return this;
    }

    public String getName() {
      return name;
    }

    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    public void updateFrom(InformerConfiguration<R> informerConfig) {
      if (informerConfig != null) {
        final var informerConfigName = informerConfig.getName();
        if (informerConfigName != null) {
          this.name = informerConfigName;
        }
        config.withNamespaces(informerConfig.getNamespaces())
            .withFollowControllerNamespacesChanges(
                informerConfig.getFollowControllerNamespacesChanges())
            .withLabelSelector(informerConfig.getLabelSelector())
            .withItemStore(informerConfig.getItemStore())
            .withOnAddFilter(informerConfig.getOnAddFilter())
            .withOnUpdateFilter(informerConfig.getOnUpdateFilter())
            .withOnDeleteFilter(informerConfig.getOnDeleteFilter())
            .withGenericFilter(informerConfig.getGenericFilter())
            .withInformerListLimit(informerConfig.getInformerListLimit());
      }
    }

    public InformerEventSourceConfiguration<R> build() {
      if (groupVersionKind != null
          && !GenericKubernetesResource.class.isAssignableFrom(resourceClass)) {
        throw new IllegalStateException(
            "If GroupVersionKind is set the resource type must be GenericKubernetesDependentResource");
      }

      return new DefaultInformerEventSourceConfiguration<>(
          groupVersionKind,
          primaryToSecondaryMapper,
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReferences(HasMetadata.getApiVersion(primaryResourceClass),
                  HasMetadata.getKind(primaryResourceClass), false)),
          config.build(), kubernetesClient);
    }
  }
}
