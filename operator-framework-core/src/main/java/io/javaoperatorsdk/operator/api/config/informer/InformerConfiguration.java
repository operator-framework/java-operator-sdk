package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public interface InformerConfiguration<R extends HasMetadata>
    extends ResourceConfiguration<R> {

  class DefaultInformerConfiguration<R extends HasMetadata> extends
      DefaultResourceConfiguration<R> implements InformerConfiguration<R> {

    private final PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private final boolean followControllerNamespaceChanges;
    private final OnDeleteFilter<R> onDeleteFilter;
    private final ItemStore<R> itemStore;

    protected DefaultInformerConfiguration(String labelSelector,
        Class<R> resourceClass,
        PrimaryToSecondaryMapper<?> primaryToSecondaryMapper,
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper,
        Set<String> namespaces, boolean followControllerNamespaceChanges,
        OnAddFilter<R> onAddFilter,
        OnUpdateFilter<R> onUpdateFilter,
        OnDeleteFilter<R> onDeleteFilter,
        GenericFilter<R> genericFilter,
        ItemStore<R> itemStore) {
      super(labelSelector, resourceClass, onAddFilter, onUpdateFilter, genericFilter, namespaces,
          itemStore);
      this.followControllerNamespaceChanges = followControllerNamespaceChanges;

      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      this.secondaryToPrimaryMapper =
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReference());
      this.onDeleteFilter = onDeleteFilter;
      this.itemStore = itemStore;
    }

    @Override
    public boolean followControllerNamespaceChanges() {
      return followControllerNamespaceChanges;
    }

    @Override
    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    public Optional<OnDeleteFilter<R>> onDeleteFilter() {
      return Optional.ofNullable(onDeleteFilter);
    }

    @Override
    public <P extends HasMetadata> PrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper() {
      return (PrimaryToSecondaryMapper<P>) primaryToSecondaryMapper;
    }

    @Override
    public Optional<ItemStore<R>> itemStore() {
      return Optional.ofNullable(this.itemStore);
    }
  }

  /**
   * Used in case the watched namespaces are changed dynamically, thus when operator is running (See
   * {@link io.javaoperatorsdk.operator.RegisteredController}). If true, changing the target
   * namespaces of a controller would result to change target namespaces for the
   * InformerEventSource.
   */
  boolean followControllerNamespaceChanges();

  SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper();

  Optional<OnAddFilter<R>> onAddFilter();

  Optional<OnUpdateFilter<R>> onUpdateFilter();

  Optional<OnDeleteFilter<R>> onDeleteFilter();

  Optional<GenericFilter<R>> genericFilter();

  <P extends HasMetadata> PrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper();

  @SuppressWarnings("unused")
  class InformerConfigurationBuilder<R extends HasMetadata> {

    private PrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private Set<String> namespaces;
    private String labelSelector;
    private final Class<R> resourceClass;
    private OnAddFilter<R> onAddFilter;
    private OnUpdateFilter<R> onUpdateFilter;
    private OnDeleteFilter<R> onDeleteFilter;
    private GenericFilter<R> genericFilter;
    private boolean inheritControllerNamespacesOnChange = false;
    private ItemStore<R> itemStore;

    private InformerConfigurationBuilder(Class<R> resourceClass) {
      this.resourceClass = resourceClass;
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

    public InformerConfigurationBuilder<R> withOnAddFilter(OnAddFilter<R> onAddFilter) {
      this.onAddFilter = onAddFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withOnUpdateFilter(OnUpdateFilter<R> onUpdateFilter) {
      this.onUpdateFilter = onUpdateFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withOnDeleteFilter(
        OnDeleteFilter<R> onDeleteFilter) {
      this.onDeleteFilter = onDeleteFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withGenericFilter(GenericFilter<R> genericFilter) {
      this.genericFilter = genericFilter;
      return this;
    }

    public InformerConfigurationBuilder<R> withOnDeleteFilter(ItemStore<R> itemStore) {
      this.itemStore = itemStore;
      return this;
    }

    public InformerConfiguration<R> build() {
      return new DefaultInformerConfiguration<>(labelSelector, resourceClass,
          primaryToSecondaryMapper,
          secondaryToPrimaryMapper,
          namespaces, inheritControllerNamespacesOnChange, onAddFilter, onUpdateFilter,
          onDeleteFilter, genericFilter, itemStore);
    }
  }

  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      Class<R> resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass);
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

  @SuppressWarnings("unchecked")
  @Override
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        InformerConfiguration.class);
  }
}
