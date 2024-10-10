package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfigHolder;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.*;

public interface InformerConfiguration<R extends HasMetadata>
    extends ResourceConfiguration<R> {

  boolean DEFAULT_FOLLOW_CONTROLLER_NAMESPACES_ON_CHANGE = true;

  static boolean inheritsNamespacesFromController(Set<String> namespaces) {
    return SAME_AS_CONTROLLER_NAMESPACES_SET.equals(namespaces);
  }

  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      Class<R> resourceClass, Class<? extends HasMetadata> primaryResourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass, primaryResourceClass);
  }

  static InformerConfigurationBuilder<GenericKubernetesResource> from(
      GroupVersionKind groupVersionKind, Class<? extends HasMetadata> primaryResourceClass) {
    return new InformerConfigurationBuilder<>(groupVersionKind, primaryResourceClass);
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
    return getInformerConfig().isFollowControllerNamespacesOnChange();
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

  default Optional<OnDeleteFilter<? super R>> onDeleteFilter() {
    return Optional.ofNullable(getInformerConfig().getOnDeleteFilter());
  }

  <P extends HasMetadata> PrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper();

  Optional<GroupVersionKind> getGroupVersionKind();

  default String name() {
    return getInformerConfig().getName();
  }

  @SuppressWarnings("unchecked")
  @Override
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        InformerConfiguration.class);
  }

  class DefaultInformerConfiguration<R extends HasMetadata> extends
      DefaultResourceConfiguration<R> implements InformerConfiguration<R> {
    private final PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private final GroupVersionKind groupVersionKind;

    protected DefaultInformerConfiguration(
        Class<R> resourceClass,
        GroupVersionKind groupVersionKind,
        PrimaryToSecondaryMapper<?> primaryToSecondaryMapper,
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper,
        InformerConfigHolder<R> informerConfig) {
      super(resourceClass, informerConfig);
      this.groupVersionKind = groupVersionKind;
      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
    }

    @Override
    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    @Override
    public Optional<OnDeleteFilter<? super R>> onDeleteFilter() {
      return Optional.ofNullable(getInformerConfig().getOnDeleteFilter());
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

    public boolean inheritsNamespacesFromController() {
      return InformerConfiguration.inheritsNamespacesFromController(getNamespaces());
    }

    @Override
    public Set<String> getEffectiveNamespaces(ControllerConfiguration<?> controllerConfiguration) {
      if (inheritsNamespacesFromController()) {
        return controllerConfiguration.getEffectiveNamespaces();
      } else {
        return super.getEffectiveNamespaces(controllerConfiguration);
      }
    }
  }


  @SuppressWarnings({"unused", "UnusedReturnValue"})
  class InformerConfigurationBuilder<R extends HasMetadata> {

    private final Class<R> resourceClass;
    private final GroupVersionKind groupVersionKind;
    private final Class<? extends HasMetadata> primaryResourceClass;
    private String name;
    private PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private final InformerConfigHolder<R>.Builder config;

    private InformerConfigurationBuilder(Class<R> resourceClass,
        Class<? extends HasMetadata> primaryResourceClass) {
      this(resourceClass, primaryResourceClass, null);
    }

    @SuppressWarnings("unchecked")
    private InformerConfigurationBuilder(GroupVersionKind groupVersionKind,
        Class<? extends HasMetadata> primaryResourceClass) {
      this((Class<R>) GenericKubernetesResource.class, primaryResourceClass, groupVersionKind);
    }

    private InformerConfigurationBuilder(Class<R> resourceClass,
        Class<? extends HasMetadata> primaryResourceClass, GroupVersionKind groupVersionKind) {
      this.resourceClass = resourceClass;
      this.groupVersionKind = groupVersionKind;
      this.primaryResourceClass = primaryResourceClass;
      this.config = InformerConfigHolder.builder(resourceClass);
    }

    public InformerConfigurationBuilder<R> withInformerConfiguration(
        Consumer<InformerConfigHolder<R>.Builder> configurator) {
      configurator.accept(config);
      return this;
    }

    public InformerConfigurationBuilder<R> withName(String name) {
      this.name = name;
      config.withName(name);
      return this;
    }

    public <P extends HasMetadata> InformerConfigurationBuilder<R> withPrimaryToSecondaryMapper(
        PrimaryToSecondaryMapper<P> primaryToSecondaryMapper) {
      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      return this;
    }

    public InformerConfigurationBuilder<R> withSecondaryToPrimaryMapper(
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
      this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
      return this;
    }

    public String getName() {
      return name;
    }

    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    public InformerConfiguration<R> build() {
      if (groupVersionKind != null
          && !GenericKubernetesResource.class.isAssignableFrom(resourceClass)) {
        throw new IllegalStateException(
            "If GroupVersionKind is set the resource type must be GenericKubernetesDependentResource");
      }

      return new DefaultInformerConfiguration<>(resourceClass,
          groupVersionKind,
          primaryToSecondaryMapper,
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReferences(HasMetadata.getApiVersion(primaryResourceClass),
                  HasMetadata.getKind(primaryResourceClass), false)),
          config.buildForInformerEventSource());
    }
  }
}
