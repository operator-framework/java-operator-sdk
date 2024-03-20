package io.javaoperatorsdk.operator.api.config.informer;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public interface InformerConfiguration<R extends HasMetadata>
    extends ResourceConfiguration<R> {

  class DefaultInformerConfiguration<R extends HasMetadata> extends
      DefaultResourceConfiguration<R> implements InformerConfiguration<R> {

    private final PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private final boolean followControllerNamespaceChanges;
    private final OnDeleteFilter<? super R> onDeleteFilter;
    private final GroupVersionKind groupVersionKind;

    protected DefaultInformerConfiguration(String labelSelector,
        Class<R> resourceClass,
        GroupVersionKind groupVersionKind,
        PrimaryToSecondaryMapper<?> primaryToSecondaryMapper,
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper,
        Set<String> namespaces, boolean followControllerNamespaceChanges,
        OnAddFilter<? super R> onAddFilter,
        OnUpdateFilter<? super R> onUpdateFilter,
        OnDeleteFilter<? super R> onDeleteFilter,
        GenericFilter<? super R> genericFilter,
        ItemStore<R> itemStore, Long informerListLimit) {
      super(resourceClass, namespaces, labelSelector, onAddFilter, onUpdateFilter, genericFilter,
          itemStore, informerListLimit);
      this.followControllerNamespaceChanges = followControllerNamespaceChanges;
      this.groupVersionKind = groupVersionKind;
      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      this.secondaryToPrimaryMapper =
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReference());
      this.onDeleteFilter = onDeleteFilter;
    }

    @Override
    public boolean followControllerNamespaceChanges() {
      return followControllerNamespaceChanges;
    }

    @Override
    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    @Override
    public Optional<OnDeleteFilter<? super R>> onDeleteFilter() {
      return Optional.ofNullable(onDeleteFilter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P extends HasMetadata> PrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper() {
      return (PrimaryToSecondaryMapper<P>) primaryToSecondaryMapper;
    }

    public Optional<GroupVersionKind> getGroupVersionKind() {
      return Optional.ofNullable(groupVersionKind);
    }
  }

  /**
   * Used in case the watched namespaces are changed dynamically, thus when operator is running (See
   * {@link io.javaoperatorsdk.operator.RegisteredController}). If true, changing the target
   * namespaces of a controller would result to change target namespaces for the
   * InformerEventSource.
   *
   * @return if namespace changes should be followed
   */
  boolean followControllerNamespaceChanges();

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

  @Override
  Optional<OnAddFilter<? super R>> onAddFilter();

  @Override
  Optional<OnUpdateFilter<? super R>> onUpdateFilter();

  Optional<OnDeleteFilter<? super R>> onDeleteFilter();

  @Override
  Optional<GenericFilter<? super R>> genericFilter();

  <P extends HasMetadata> PrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper();

  Optional<GroupVersionKind> getGroupVersionKind();

  @SuppressWarnings("unused")
  class InformerConfigurationBuilder<R extends HasMetadata> {

    private final Class<R> resourceClass;
    private final GroupVersionKind groupVersionKind;
    private PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private Set<String> namespaces;
    private String labelSelector;
    private OnAddFilter<? super R> onAddFilter;
    private OnUpdateFilter<? super R> onUpdateFilter;
    private OnDeleteFilter<? super R> onDeleteFilter;
    private GenericFilter<? super R> genericFilter;
    private boolean inheritControllerNamespacesOnChange = false;
    private ItemStore<R> itemStore;
    private Long informerListLimit;

    private InformerConfigurationBuilder(Class<R> resourceClass) {
      this.resourceClass = resourceClass;
      this.groupVersionKind = null;
    }

    @SuppressWarnings("unchecked")
    private InformerConfigurationBuilder(GroupVersionKind groupVersionKind) {
      this.resourceClass = (Class<R>) GenericKubernetesResource.class;
      this.groupVersionKind = groupVersionKind;
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
      this.inheritControllerNamespacesOnChange = true;
      return this;
    }

    /**
     * Configures the informer to watch and track the same namespaces as the parent
     * {@link io.javaoperatorsdk.operator.processing.Controller}, meaning that the informer will be
     * restarted to watch the new namespaces if the parent controller's namespace configuration
     * changes.
     *
     * @param context {@link EventSourceContext} from which the parent
     *        {@link io.javaoperatorsdk.operator.processing.Controller}'s configuration is retrieved
     * @param <P> the primary resource type associated with the parent controller
     * @return the builder instance so that calls can be chained fluently
     */
    public <P extends HasMetadata> InformerConfigurationBuilder<R> withNamespacesInheritedFromController(
        EventSourceContext<P> context) {
      namespaces = context.getControllerConfiguration().getEffectiveNamespaces();
      this.inheritControllerNamespacesOnChange = true;
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
    public InformerConfigurationBuilder<R> followNamespaceChanges(boolean followChanges) {
      this.inheritControllerNamespacesOnChange = followChanges;
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

    public InformerConfiguration<R> build() {
      return new DefaultInformerConfiguration<>(labelSelector, resourceClass, groupVersionKind,
          primaryToSecondaryMapper,
          secondaryToPrimaryMapper,
          namespaces, inheritControllerNamespacesOnChange, onAddFilter, onUpdateFilter,
          onDeleteFilter, genericFilter, itemStore, informerListLimit);
    }
  }

  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      Class<R> resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass);
  }

  /**
   * * For the case when want to use {@link GenericKubernetesResource}
   */
  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      GroupVersionKind groupVersionKind) {
    return new InformerConfigurationBuilder<>(groupVersionKind);
  }

  /**
   * Creates a configuration builder that inherits namespaces from the controller and follows
   * namespaces changes.
   *
   * @param resourceClass secondary resource class
   * @param eventSourceContext of the initializer
   * @return builder
   * @param <R> secondary resource type
   */
  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      Class<R> resourceClass, EventSourceContext<?> eventSourceContext) {
    return new InformerConfigurationBuilder<>(resourceClass)
        .withNamespacesInheritedFromController(eventSourceContext);
  }

  /**
   * * For the case when want to use {@link GenericKubernetesResource}
   */
  @SuppressWarnings("unchecked")
  static InformerConfigurationBuilder<GenericKubernetesResource> from(
      GroupVersionKind groupVersionKind, EventSourceContext<?> eventSourceContext) {
    return new InformerConfigurationBuilder<GenericKubernetesResource>(groupVersionKind)
        .withNamespacesInheritedFromController(eventSourceContext);
  }

  @SuppressWarnings("unchecked")
  @Override
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        InformerConfiguration.class);
  }
}
