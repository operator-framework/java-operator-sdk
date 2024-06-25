package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;


@SuppressWarnings("unused")
public class InformerConfigHolder<R extends HasMetadata> {
  @SuppressWarnings("rawtypes")
  public static final InformerConfigHolder DEFAULT_CONTROLLER_CONFIG =
      InformerConfigHolder.builder().buildForController();
  @SuppressWarnings("rawtypes")
  public static final InformerConfigHolder DEFAULT_EVENT_SOURCE_CONFIG =
      InformerConfigHolder.builder().buildForInformerEventSource();

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
  private final Builder builder = new Builder();

  public InformerConfigHolder(String name, Set<String> namespaces,
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

  private InformerConfigHolder() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> InformerConfigHolder<R>.Builder builder() {
    return new InformerConfigHolder().builder;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> InformerConfigHolder<R>.Builder builder(
      Class<R> resourceClass) {
    return new InformerConfigHolder().builder;
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

  void updateInformerConfigBuilder(
      InformerConfiguration.InformerConfigurationBuilder<R> builder) {
    if (name != null) {
      builder.withName(name);
    }
    builder.withNamespaces(namespaces);
    builder.followControllerNamespacesOnChange(followControllerNamespacesOnChange);
    builder.withLabelSelector(labelSelector);
    builder.withItemStore(itemStore);
    builder.withOnAddFilter(onAddFilter);
    builder.withOnUpdateFilter(onUpdateFilter);
    builder.withOnDeleteFilter(onDeleteFilter);
    builder.withGenericFilter(genericFilter);
    builder.withInformerListLimit(informerListLimit);
  }

  @SuppressWarnings("UnusedReturnValue")
  public class Builder {

    public InformerConfigHolder<R> buildForController() {
      if (namespaces == null || namespaces.isEmpty()) {
        namespaces = Constants.DEFAULT_NAMESPACES_SET;
      }
      return InformerConfigHolder.this;
    }

    public InformerConfigHolder<R> buildForInformerEventSource() {
      if (namespaces == null || namespaces.isEmpty()) {
        namespaces = Constants.SAME_AS_CONTROLLER_NAMESPACES_SET;
      }
      if (followControllerNamespacesOnChange == null) {
        followControllerNamespacesOnChange =
            InformerConfiguration.DEFAULT_FOLLOW_CONTROLLER_NAMESPACES_ON_CHANGE;
      }
      return InformerConfigHolder.this;
    }

    @SuppressWarnings({"unchecked"})
    public InformerConfigHolder<R>.Builder initFromAnnotation(InformerConfig informerConfig,
        String context) {
      final var config = InformerConfigHolder.builder();
      if (informerConfig != null) {

        // override default name if more specific one is provided
        if (!Constants.NO_VALUE_SET.equals(informerConfig.name())) {
          config.withName(informerConfig.name());
        }

        var namespaces = Set.of(informerConfig.namespaces());
        config.withNamespaces(namespaces);

        final var fromAnnotation = informerConfig.labelSelector();
        var labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;
        config.withLabelSelector(labelSelector);

        config.withOnAddFilter(Utils.instantiate(informerConfig.onAddFilter(),
            OnAddFilter.class, context));

        config.withOnUpdateFilter(Utils.instantiate(informerConfig.onUpdateFilter(),
            OnUpdateFilter.class, context));

        config.withOnDeleteFilter(Utils.instantiate(informerConfig.onDeleteFilter(),
            OnDeleteFilter.class, context));

        config.withGenericFilter(Utils.instantiate(informerConfig.genericFilter(),
            GenericFilter.class,
            context));

        config.withFollowControllerNamespacesOnChange(
            informerConfig.followControllerNamespacesOnChange());

        config.withItemStore(Utils.instantiate(informerConfig.itemStore(),
            ItemStore.class, context));

        final var informerListLimitValue = informerConfig.informerListLimit();
        final var informerListLimit =
            informerListLimitValue == Constants.NO_LONG_VALUE_SET ? null : informerListLimitValue;
        config.withInformerListLimit(informerListLimit);
      }
      return (InformerConfigHolder<R>.Builder) config;
    }

    public Builder withName(String name) {
      InformerConfigHolder.this.name = name;
      return this;
    }

    public Builder withNamespaces(Set<String> namespaces) {
      InformerConfigHolder.this.namespaces =
          ResourceConfiguration.ensureValidNamespaces(namespaces);
      return this;
    }

    public Builder withFollowControllerNamespacesOnChange(
        boolean followControllerNamespacesOnChange) {
      InformerConfigHolder.this.followControllerNamespacesOnChange =
          followControllerNamespacesOnChange;;
      return this;
    }

    public Builder withLabelSelector(String labelSelector) {
      InformerConfigHolder.this.labelSelector =
          ResourceConfiguration.ensureValidLabelSelector(labelSelector);
      return this;
    }

    public Builder withOnAddFilter(
        OnAddFilter<? super R> onAddFilter) {
      InformerConfigHolder.this.onAddFilter = onAddFilter;
      return this;
    }

    public Builder withOnUpdateFilter(
        OnUpdateFilter<? super R> onUpdateFilter) {
      InformerConfigHolder.this.onUpdateFilter = onUpdateFilter;
      return this;
    }

    public Builder withOnDeleteFilter(
        OnDeleteFilter<? super R> onDeleteFilter) {
      InformerConfigHolder.this.onDeleteFilter = onDeleteFilter;
      return this;
    }

    public Builder withGenericFilter(
        GenericFilter<? super R> genericFilter) {
      InformerConfigHolder.this.genericFilter = genericFilter;
      return this;
    }

    public Builder withItemStore(ItemStore<R> itemStore) {
      InformerConfigHolder.this.itemStore = itemStore;
      return this;
    }

    public Builder withInformerListLimit(Long informerListLimit) {
      InformerConfigHolder.this.informerListLimit = informerListLimit;
      return this;
    }
  }
}
