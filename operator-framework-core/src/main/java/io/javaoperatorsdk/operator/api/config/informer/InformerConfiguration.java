package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfigHolder;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
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
    private Set<String> namespaces = SAME_AS_CONTROLLER_NAMESPACES_SET;
    private String labelSelector;
    private OnAddFilter<? super R> onAddFilter;
    private OnUpdateFilter<? super R> onUpdateFilter;
    private OnDeleteFilter<? super R> onDeleteFilter;
    private GenericFilter<? super R> genericFilter;
    private ItemStore<R> itemStore;
    private Long informerListLimit;
    private boolean followControllerNamespacesOnChange =
        DEFAULT_FOLLOW_CONTROLLER_NAMESPACES_ON_CHANGE;

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
    }

    public InformerConfigurationBuilder<R> withName(String name) {
      this.name = name;
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

    public InformerConfigurationBuilder<R> withNamespaces(String... namespaces) {
      return withNamespaces(
          namespaces != null ? Set.of(namespaces) : DEFAULT_NAMESPACES_SET);
    }

    public InformerConfigurationBuilder<R> withNamespaces(Set<String> namespaces) {
      return withNamespaces(namespaces, false);
    }

    /**
     * Sets the initial set of namespaces to watch (typically extracted from the parent
     * {@link io.javaoperatorsdk.operator.processing.Controller}'s configuration), specifying
     * whether changes made to the parent controller configured namespaces should be tracked or not.
     *
     * @param namespaces the initial set of namespaces to watch
     * @param followChanges {@code true} to follow the changes made to the parent controller
     *        namespaces, {@code false} otherwise
     * @return the builder instance so that calls can be chained fluently
     */
    public InformerConfigurationBuilder<R> withNamespaces(Set<String> namespaces,
        boolean followChanges) {
      this.namespaces = namespaces != null ? namespaces : DEFAULT_NAMESPACES_SET;
      this.followControllerNamespacesOnChange = followChanges;
      return this;
    }

    public <P extends HasMetadata> InformerConfigurationBuilder<R> withNamespacesInheritedFromController() {
      this.namespaces = SAME_AS_CONTROLLER_NAMESPACES_SET;
      return this;
    }

    public <P extends HasMetadata> InformerConfigurationBuilder<R> withWatchAllNamespaces() {
      this.namespaces = WATCH_ALL_NAMESPACE_SET;
      return this;
    }

    public <P extends HasMetadata> InformerConfigurationBuilder<R> withWatchCurrentNamespace() {
      this.namespaces = WATCH_CURRENT_NAMESPACE_SET;
      return this;
    }

    /**
     * Whether the associated informer should track changes made to the parent
     * {@link io.javaoperatorsdk.operator.processing.Controller}'s namespaces configuration.
     *
     * @param followChanges {@code true} to reconfigure the associated informer when the parent
     *        controller's namespaces are reconfigured, {@code false} otherwise
     * @return the builder instance so that calls can be chained fluently
     */
    public InformerConfigurationBuilder<R> followControllerNamespacesOnChange(
        boolean followChanges) {
      this.followControllerNamespacesOnChange = followChanges;
      return this;
    }

    public InformerConfigurationBuilder<R> withLabelSelector(String labelSelector) {
      this.labelSelector = labelSelector;
      return this;
    }

    public InformerConfigurationBuilder<R> withOnAddFilter(OnAddFilter<? super R> onAddFilter) {
      this.onAddFilter = onAddFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withOnUpdateFilter(
        OnUpdateFilter<? super R> onUpdateFilter) {
      this.onUpdateFilter = onUpdateFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withOnDeleteFilter(
        OnDeleteFilter<? super R> onDeleteFilter) {
      this.onDeleteFilter = onDeleteFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withGenericFilter(
        GenericFilter<? super R> genericFilter) {
      this.genericFilter = genericFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withItemStore(ItemStore<R> itemStore) {
      this.itemStore = itemStore;
      return this;
    }

    /**
     * Sets a max page size limit when starting the informer. This will result in pagination while
     * populating the cache. This means that longer lists will take multiple requests to fetch. See
     * {@link io.fabric8.kubernetes.client.dsl.Informable#withLimit(Long)} for more details.
     *
     * @param informerListLimit null (the default) results in no pagination
     */
    public InformerConfigurationBuilder<R> withInformerListLimit(Long informerListLimit) {
      this.informerListLimit = informerListLimit;
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

      // todo: should use an informer builder directly and share code across similar builders
      InformerConfigHolder<R> informerConfig = InformerConfigHolder.builder(resourceClass)
          .withLabelSelector(labelSelector)
          .withItemStore(itemStore)
          .withInformerListLimit(informerListLimit)
          .withFollowControllerNamespacesOnChange(followControllerNamespacesOnChange)
          .withName(name)
          .withNamespaces(namespaces)
          .withOnAddFilter(onAddFilter)
          .withOnDeleteFilter(onDeleteFilter)
          .withOnUpdateFilter(onUpdateFilter)
          .withGenericFilter(genericFilter)
          .buildForInformerEventSource();

      return new DefaultInformerConfiguration<>(resourceClass,
          groupVersionKind,
          primaryToSecondaryMapper,
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReferences(HasMetadata.getApiVersion(primaryResourceClass),
                  HasMetadata.getKind(primaryResourceClass), false)),
          informerConfig);
    }
  }
}
