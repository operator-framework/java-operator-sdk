package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.*;


@SuppressWarnings("unused")
public class InformerConfiguration<R extends HasMetadata> {
  private final Builder builder = new Builder();
  private String name;
  private Set<String> namespaces;
  private Boolean followControllerNamespacesOnChange;
  private String labelSelector;
  private OnAddFilter<? super R> onAddFilter;
  private OnUpdateFilter<? super R> onUpdateFilter;
  private OnDeleteFilter<? super R> onDeleteFilter;
  private GenericFilter<? super R> genericFilter;
  private ItemStore<R> itemStore;
  private Long informerListLimit;

  public InformerConfiguration(String name, Set<String> namespaces,
      boolean followControllerNamespacesOnChange,
      String labelSelector, OnAddFilter<? super R> onAddFilter,
      OnUpdateFilter<? super R> onUpdateFilter, OnDeleteFilter<? super R> onDeleteFilter,
      GenericFilter<? super R> genericFilter, ItemStore<R> itemStore, Long informerListLimit) {
    this.name = name;
    this.namespaces = namespaces;
    this.followControllerNamespacesOnChange = followControllerNamespacesOnChange;
    this.labelSelector = labelSelector;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.onDeleteFilter = onDeleteFilter;
    this.genericFilter = genericFilter;
    this.itemStore = itemStore;
    this.informerListLimit = informerListLimit;
  }

  private InformerConfiguration() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> InformerConfiguration<R>.Builder builder() {
    return new InformerConfiguration().builder;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> InformerConfiguration<R>.Builder builder(
      Class<R> resourceClass) {
    return new InformerConfiguration().builder;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> InformerConfiguration<R>.Builder builder(
      InformerConfiguration<R> original) {
    return new InformerConfiguration(original.name, original.namespaces,
        original.followControllerNamespacesOnChange, original.labelSelector, original.onAddFilter,
        original.onUpdateFilter, original.onDeleteFilter, original.genericFilter,
        original.itemStore, original.informerListLimit).builder;
  }

  public String getName() {
    return name;
  }

  public Set<String> getNamespaces() {
    return namespaces;
  }

  public boolean isFollowControllerNamespacesOnChange() {
    return followControllerNamespacesOnChange;
  }

  public String getLabelSelector() {
    return labelSelector;
  }

  public OnAddFilter<? super R> getOnAddFilter() {
    return onAddFilter;
  }

  public OnUpdateFilter<? super R> getOnUpdateFilter() {
    return onUpdateFilter;
  }

  public OnDeleteFilter<? super R> getOnDeleteFilter() {
    return onDeleteFilter;
  }

  public GenericFilter<? super R> getGenericFilter() {
    return genericFilter;
  }

  public ItemStore<R> getItemStore() {
    return itemStore;
  }

  public Long getInformerListLimit() {
    return informerListLimit;
  }


  @SuppressWarnings("UnusedReturnValue")
  public class Builder {

    public InformerConfiguration<R> buildForController() {
      // if the informer config uses the default "same as controller" value, reset the namespaces to
      // the default set for controllers
      if (namespaces == null || namespaces.isEmpty()
          || InformerEventSourceConfiguration.inheritsNamespacesFromController(namespaces)) {
        namespaces = Constants.DEFAULT_NAMESPACES_SET;
      }
      return InformerConfiguration.this;
    }

    public InformerConfiguration<R> buildForInformerEventSource() {
      if (namespaces == null || namespaces.isEmpty()) {
        namespaces = Constants.SAME_AS_CONTROLLER_NAMESPACES_SET;
      }
      if (followControllerNamespacesOnChange == null) {
        followControllerNamespacesOnChange =
            InformerEventSourceConfiguration.DEFAULT_FOLLOW_CONTROLLER_NAMESPACES_ON_CHANGE;
      }
      return InformerConfiguration.this;
    }

    @SuppressWarnings({"unchecked"})
    public InformerConfiguration<R>.Builder initFromAnnotation(Informer informerConfig,
        String context) {
      if (informerConfig != null) {

        // override default name if more specific one is provided
        if (!Constants.NO_VALUE_SET.equals(informerConfig.name())) {
          withName(informerConfig.name());
        }

        var namespaces = Set.of(informerConfig.namespaces());
        withNamespaces(namespaces);

        final var fromAnnotation = informerConfig.labelSelector();
        var labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;
        withLabelSelector(labelSelector);

        withOnAddFilter(Utils.instantiate(informerConfig.onAddFilter(),
            OnAddFilter.class, context));

        withOnUpdateFilter(Utils.instantiate(informerConfig.onUpdateFilter(),
            OnUpdateFilter.class, context));

        withOnDeleteFilter(Utils.instantiate(informerConfig.onDeleteFilter(),
            OnDeleteFilter.class, context));

        withGenericFilter(Utils.instantiate(informerConfig.genericFilter(),
            GenericFilter.class,
            context));

        withFollowControllerNamespacesOnChange(
            informerConfig.followControllerNamespacesOnChange());

        withItemStore(Utils.instantiate(informerConfig.itemStore(),
            ItemStore.class, context));

        final var informerListLimitValue = informerConfig.informerListLimit();
        final var informerListLimit =
            informerListLimitValue == Constants.NO_LONG_VALUE_SET ? null : informerListLimitValue;
        withInformerListLimit(informerListLimit);
      }
      return this;
    }

    public Builder withName(String name) {
      InformerConfiguration.this.name = name;
      return this;
    }

    public Builder withNamespaces(Set<String> namespaces) {
      InformerConfiguration.this.namespaces =
          ResourceConfiguration.ensureValidNamespaces(namespaces);
      return this;
    }

    public Set<String> namespaces() {
      return Set.copyOf(namespaces);
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
    public Builder withNamespaces(Set<String> namespaces, boolean followChanges) {
      withNamespaces(namespaces).withFollowControllerNamespacesOnChange(followChanges);
      return this;
    }

    public Builder withNamespacesInheritedFromController() {
      withNamespaces(SAME_AS_CONTROLLER_NAMESPACES_SET);
      return this;
    }

    public Builder withWatchAllNamespaces() {
      withNamespaces(WATCH_ALL_NAMESPACE_SET);
      return this;
    }

    public Builder withWatchCurrentNamespace() {
      withNamespaces(WATCH_CURRENT_NAMESPACE_SET);
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
    public Builder withFollowControllerNamespacesOnChange(boolean followChanges) {
      InformerConfiguration.this.followControllerNamespacesOnChange =
          followChanges;
      return this;
    }

    public Builder withLabelSelector(String labelSelector) {
      InformerConfiguration.this.labelSelector =
          ResourceConfiguration.ensureValidLabelSelector(labelSelector);
      return this;
    }

    public Builder withOnAddFilter(
        OnAddFilter<? super R> onAddFilter) {
      InformerConfiguration.this.onAddFilter = onAddFilter;
      return this;
    }

    public Builder withOnUpdateFilter(
        OnUpdateFilter<? super R> onUpdateFilter) {
      InformerConfiguration.this.onUpdateFilter = onUpdateFilter;
      return this;
    }

    public Builder withOnDeleteFilter(
        OnDeleteFilter<? super R> onDeleteFilter) {
      InformerConfiguration.this.onDeleteFilter = onDeleteFilter;
      return this;
    }

    public Builder withGenericFilter(
        GenericFilter<? super R> genericFilter) {
      InformerConfiguration.this.genericFilter = genericFilter;
      return this;
    }

    public Builder withItemStore(ItemStore<R> itemStore) {
      InformerConfiguration.this.itemStore = itemStore;
      return this;
    }

    public Builder withInformerListLimit(Long informerListLimit) {
      InformerConfiguration.this.informerListLimit = informerListLimit;
      return this;
    }
  }
}
