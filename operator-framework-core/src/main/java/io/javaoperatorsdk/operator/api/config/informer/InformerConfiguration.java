package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.cache.BoundedItemStore;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.*;

@SuppressWarnings("unused")
public class InformerConfiguration<R extends HasMetadata> {
  private final Builder builder = new Builder();
  private final Class<R> resourceClass;
  private final String resourceTypeName;
  private String name;
  private Set<String> namespaces;
  private Boolean followControllerNamespaceChanges;
  private String labelSelector;
  private OnAddFilter<? super R> onAddFilter;
  private OnUpdateFilter<? super R> onUpdateFilter;
  private OnDeleteFilter<? super R> onDeleteFilter;
  private GenericFilter<? super R> genericFilter;
  private ItemStore<R> itemStore;
  private Long informerListLimit;
  private FieldSelector fieldSelector;

  protected InformerConfiguration(
      Class<R> resourceClass,
      String name,
      Set<String> namespaces,
      boolean followControllerNamespaceChanges,
      String labelSelector,
      OnAddFilter<? super R> onAddFilter,
      OnUpdateFilter<? super R> onUpdateFilter,
      OnDeleteFilter<? super R> onDeleteFilter,
      GenericFilter<? super R> genericFilter,
      ItemStore<R> itemStore,
      Long informerListLimit,
      FieldSelector fieldSelector) {
    this(resourceClass);
    this.name = name;
    this.namespaces = namespaces;
    this.followControllerNamespaceChanges = followControllerNamespaceChanges;
    this.labelSelector = labelSelector;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.onDeleteFilter = onDeleteFilter;
    this.genericFilter = genericFilter;
    this.itemStore = itemStore;
    this.informerListLimit = informerListLimit;
    this.fieldSelector = fieldSelector;
  }

  private InformerConfiguration(Class<R> resourceClass) {
    this.resourceClass = resourceClass;
    this.resourceTypeName =
        resourceClass.isAssignableFrom(GenericKubernetesResource.class)
            // in general this is irrelevant now for secondary resources it is used just by
            // controller
            // where GenericKubernetesResource now does not apply
            ? GenericKubernetesResource.class.getSimpleName()
            : ReconcilerUtils.getResourceTypeName(resourceClass);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> InformerConfiguration<R>.Builder builder(
      Class<R> resourceClass) {
    return new InformerConfiguration(resourceClass).builder;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> InformerConfiguration<R>.Builder builder(
      InformerConfiguration<R> original) {
    return new InformerConfiguration(
            original.resourceClass,
            original.name,
            original.namespaces,
            original.followControllerNamespaceChanges,
            original.labelSelector,
            original.onAddFilter,
            original.onUpdateFilter,
            original.onDeleteFilter,
            original.genericFilter,
            original.itemStore,
            original.informerListLimit,
            original.fieldSelector)
        .builder;
  }

  public static String ensureValidLabelSelector(String labelSelector) {
    // might want to implement validation here?
    return labelSelector;
  }

  public static boolean allNamespacesWatched(Set<String> namespaces) {
    failIfNotValid(namespaces);
    return DEFAULT_NAMESPACES_SET.equals(namespaces);
  }

  public static boolean currentNamespaceWatched(Set<String> namespaces) {
    failIfNotValid(namespaces);
    return WATCH_CURRENT_NAMESPACE_SET.equals(namespaces);
  }

  public static void failIfNotValid(Set<String> namespaces) {
    if (namespaces != null && !namespaces.isEmpty()) {
      final var present =
          namespaces.contains(WATCH_CURRENT_NAMESPACE) || namespaces.contains(WATCH_ALL_NAMESPACES);
      if (!present || namespaces.size() == 1) {
        return;
      }
    }
    throw new IllegalArgumentException(
        "Must specify namespaces. To watch all namespaces, use only '"
            + WATCH_ALL_NAMESPACES
            + "'. To watch only the namespace in which the operator is deployed, use only '"
            + WATCH_CURRENT_NAMESPACE
            + "'");
  }

  public static Set<String> ensureValidNamespaces(Collection<String> namespaces) {
    if (namespaces != null && !namespaces.isEmpty()) {
      return namespaces.stream().map(String::trim).collect(Collectors.toSet());
    } else {
      return Constants.DEFAULT_NAMESPACES_SET;
    }
  }

  public static boolean inheritsNamespacesFromController(Set<String> namespaces) {
    return SAME_AS_CONTROLLER_NAMESPACES_SET.equals(namespaces);
  }

  public Class<R> getResourceClass() {
    return resourceClass;
  }

  public String getResourceTypeName() {
    return resourceTypeName;
  }

  public String getName() {
    return name;
  }

  public Set<String> getNamespaces() {
    return namespaces;
  }

  public boolean watchAllNamespaces() {
    return InformerConfiguration.allNamespacesWatched(getNamespaces());
  }

  public boolean watchCurrentNamespace() {
    return InformerConfiguration.currentNamespaceWatched(getNamespaces());
  }

  public boolean inheritsNamespacesFromController() {
    return inheritsNamespacesFromController(getNamespaces());
  }

  /**
   * Computes the effective namespaces based on the set specified by the user, in particular
   * retrieves the current namespace from the client when the user specified that they wanted to
   * watch the current namespace only.
   *
   * @return a Set of namespace names the associated controller will watch
   */
  public Set<String> getEffectiveNamespaces(ControllerConfiguration<?> controllerConfiguration) {
    if (inheritsNamespacesFromController()) {
      return controllerConfiguration.getEffectiveNamespaces();
    }

    var targetNamespaces = getNamespaces();
    if (watchCurrentNamespace()) {
      final String namespace =
          controllerConfiguration
              .getConfigurationService()
              .getKubernetesClient()
              .getConfiguration()
              .getNamespace();
      if (namespace == null) {
        throw new OperatorException(
            "Couldn't retrieve the currently connected namespace. Make sure it's correctly set in"
                + " your ~/.kube/config file, using, e.g. 'kubectl config set-context <your"
                + " context> --namespace=<your namespace>'");
      }
      targetNamespaces = Collections.singleton(namespace);
    }
    return targetNamespaces;
  }

  /**
   * Used in case the watched namespaces are changed dynamically, thus when operator is running (See
   * {@link io.javaoperatorsdk.operator.RegisteredController}). If true, changing the target
   * namespaces of a controller would result to change target namespaces for the
   * InformerEventSource.
   *
   * @return if namespace changes should be followed
   */
  public boolean getFollowControllerNamespaceChanges() {
    return followControllerNamespaceChanges;
  }

  /**
   * Retrieves the label selector that is used to filter which resources are actually watched by the
   * associated informer. See the official documentation on the <a
   * href="https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/">topic</a> for
   * more details on syntax.
   *
   * @return the label selector filtering watched resources
   */
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

  /**
   * Replaces the item store in informer. See underlying <a href=
   * "https://github.com/fabric8io/kubernetes-client/blob/43b67939fde91046ab7fb0c362f500c2b46eb59e/kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/DefaultSharedIndexInformer.java#L273">method</a>
   * in fabric8 client informer implementation.
   *
   * <p>The main goal, is to be able to use limited caches or provide any custom implementation.
   *
   * <p>See {@link BoundedItemStore} and <a href=
   * "https://github.com/operator-framework/java-operator-sdk/blob/main/caffeine-bounded-cache-support/src/main/java/io/javaoperatorsdk/operator/processing/event/source/cache/CaffeineBoundedCache.java">CaffeineBoundedCache</a>
   *
   * @return Optional {@link ItemStore} implementation. If present this item store will be used by
   *     the informers.
   */
  public ItemStore<R> getItemStore() {
    return itemStore;
  }

  /**
   * The maximum amount of items to return for a single list call when starting an informer. If this
   * is a not null it will result in paginating for the initial load of the informer cache.
   */
  public Long getInformerListLimit() {
    return informerListLimit;
  }

  public FieldSelector getFieldSelector() {
    return fieldSelector;
  }

  @SuppressWarnings("UnusedReturnValue")
  public class Builder {

    /** For internal usage only. Use {@link #build()} method for building for InformerEventSource */
    public InformerConfiguration<R> buildForController() {
      // if the informer config uses the default "same as controller" value, reset the namespaces to
      // the default set for controllers
      if (namespaces == null
          || namespaces.isEmpty()
          || inheritsNamespacesFromController(namespaces)) {
        namespaces = Constants.DEFAULT_NAMESPACES_SET;
      }
      // to avoid potential NPE
      followControllerNamespaceChanges = false;
      return InformerConfiguration.this;
    }

    /** Build for InformerEventSource */
    public InformerConfiguration<R> build() {
      if (namespaces == null || namespaces.isEmpty()) {
        namespaces = Constants.SAME_AS_CONTROLLER_NAMESPACES_SET;
      }
      if (followControllerNamespaceChanges == null) {
        followControllerNamespaceChanges = DEFAULT_FOLLOW_CONTROLLER_NAMESPACE_CHANGES;
      }
      return InformerConfiguration.this;
    }

    @SuppressWarnings({"unchecked"})
    public InformerConfiguration<R>.Builder initFromAnnotation(
        Informer informerConfig, String context) {
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

        withOnAddFilter(
            Utils.instantiate(informerConfig.onAddFilter(), OnAddFilter.class, context));

        withOnUpdateFilter(
            Utils.instantiate(informerConfig.onUpdateFilter(), OnUpdateFilter.class, context));

        withOnDeleteFilter(
            Utils.instantiate(informerConfig.onDeleteFilter(), OnDeleteFilter.class, context));

        withGenericFilter(
            Utils.instantiate(informerConfig.genericFilter(), GenericFilter.class, context));

        withFollowControllerNamespacesChanges(informerConfig.followControllerNamespaceChanges());

        withItemStore(Utils.instantiate(informerConfig.itemStore(), ItemStore.class, context));

        final var informerListLimitValue = informerConfig.informerListLimit();
        final var informerListLimit =
            informerListLimitValue == Constants.NO_LONG_VALUE_SET ? null : informerListLimitValue;
        withInformerListLimit(informerListLimit);

        withFieldSelector(
            new FieldSelector(
                Arrays.stream(informerConfig.fieldSelector())
                    .map(f -> new FieldSelector.Field(f.path(), f.value(), f.negate()))
                    .toList()));
      }
      return this;
    }

    public Builder withName(String name) {
      InformerConfiguration.this.name = name;
      return this;
    }

    public Builder withNamespaces(Set<String> namespaces) {
      InformerConfiguration.this.namespaces = ensureValidNamespaces(namespaces);
      return this;
    }

    public Set<String> namespaces() {
      return Set.copyOf(namespaces);
    }

    /**
     * Sets the initial set of namespaces to watch (typically extracted from the parent {@link
     * io.javaoperatorsdk.operator.processing.Controller}'s configuration), specifying whether
     * changes made to the parent controller configured namespaces should be tracked or not.
     *
     * @param namespaces the initial set of namespaces to watch
     * @param followChanges {@code true} to follow the changes made to the parent controller
     *     namespaces, {@code false} otherwise
     * @return the builder instance so that calls can be chained fluently
     */
    public Builder withNamespaces(Set<String> namespaces, boolean followChanges) {
      withNamespaces(namespaces).withFollowControllerNamespacesChanges(followChanges);
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
     * Whether the associated informer should track changes made to the parent {@link
     * io.javaoperatorsdk.operator.processing.Controller}'s namespaces configuration.
     *
     * @param followChanges {@code true} to reconfigure the associated informer when the parent
     *     controller's namespaces are reconfigured, {@code false} otherwise
     * @return the builder instance so that calls can be chained fluently
     */
    public Builder withFollowControllerNamespacesChanges(boolean followChanges) {
      InformerConfiguration.this.followControllerNamespaceChanges = followChanges;
      return this;
    }

    public Builder withLabelSelector(String labelSelector) {
      InformerConfiguration.this.labelSelector = ensureValidLabelSelector(labelSelector);
      return this;
    }

    public Builder withOnAddFilter(OnAddFilter<? super R> onAddFilter) {
      InformerConfiguration.this.onAddFilter = onAddFilter;
      return this;
    }

    public Builder withOnUpdateFilter(OnUpdateFilter<? super R> onUpdateFilter) {
      InformerConfiguration.this.onUpdateFilter = onUpdateFilter;
      return this;
    }

    public Builder withOnDeleteFilter(OnDeleteFilter<? super R> onDeleteFilter) {
      InformerConfiguration.this.onDeleteFilter = onDeleteFilter;
      return this;
    }

    public Builder withGenericFilter(GenericFilter<? super R> genericFilter) {
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

    public Builder withFieldSelector(FieldSelector fieldSelector) {
      InformerConfiguration.this.fieldSelector = fieldSelector;
      return this;
    }
  }
}
